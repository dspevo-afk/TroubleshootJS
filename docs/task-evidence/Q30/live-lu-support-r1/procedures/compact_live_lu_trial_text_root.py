from pathlib import Path
import gzip,hashlib,json,re,sys
s=Path(__file__).resolve().parent;r=Path(sys.argv[1]);p=r/'docs/task-evidence/Q30/live-lu-support-r1'
sha=lambda b:hashlib.sha256(b).hexdigest()
invraw=(p/'inventory.json').read_bytes();prior=sha(invraw)
assert prior=='91ec98f6aae6b5c5d271198a2b6e1211fa3add25d1e23b41bbd24ef0ab4a1b62'
snapshot=s/'live-lu-support-r1-inventory-before-text-compaction.json';assert not snapshot.exists();snapshot.write_bytes(invraw)
inv=json.loads(invraw);ledger=[]
for e in inv['artifacts']:
    path=p/e['path'];raw=path.read_bytes();assert sha(raw)==e['storedSha256']
    if e['path'].endswith('.gz') or (b'\r' not in raw and not re.search(rb'[ \t]+$',raw,re.M)):continue
    assert p.resolve() in path.resolve().parents
    for q in [path,*path.parents]:
        assert not q.is_symlink() and not (getattr(q.lstat(),'st_file_attributes',0)&0x400),q
        if q==p:break
    out=Path(str(path)+'.gz');assert not out.exists()
    packed=gzip.compress(raw,mtime=0);out.write_bytes(packed);assert gzip.decompress(out.read_bytes())==raw
    old=e['path'];e.update(path=old+'.gz',storedBytes=len(packed),storedSha256=sha(packed),payloadBytes=len(raw),payloadSha256=sha(raw),transform='gzip-mtime-0;'+e['transform'])
    ledger.append({'restorePath':old,'storedPath':e['path'],'payloadBytes':len(raw),'payloadSha256':sha(raw)})
    assert path.read_bytes()==raw;path.unlink()
def add(dest,raw,ref):
    out=p/dest;assert not out.exists();out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(raw)
    inv['artifacts'].append({'path':dest,'sourceRef':ref,'sourceBytes':len(raw),'sourceSha256':sha(raw),'storedBytes':len(raw),'storedSha256':sha(raw),'payloadBytes':len(raw),'payloadSha256':sha(raw),'transform':'generated' if ref.startswith('generated:') else 'exact'})
add('procedures/compact_live_lu_trial_text_root.py',Path(__file__).read_bytes(),'scratch/'+Path(__file__).name)
add('experiment/exact-text-compaction.json',(json.dumps({'priorInventorySha256':prior,'reason':'Preserve exact CRLF and trailing-whitespace payloads against Git text normalization and whitespace checks; no data or source changes','files':ledger},indent=2)+'\n').encode(),'generated:compact_live_lu_trial_text_root.py')
inv['artifacts'].sort(key=lambda e:e['path']);inv['artifactCountExcludingInventory']=len(inv['artifacts'])
raw=(json.dumps(inv,indent=2)+'\n').encode();(p/'inventory.json').write_bytes(raw);(p/'inventory.sha256').write_bytes((sha(raw)+'  inventory.json\n').encode())
receipt={'status':'EXACT_TEXT_COMPACTION_PENDING_AUDIT','priorInventorySha256':prior,'inventorySha256':sha(raw),'artifacts':len(inv['artifacts']),'compactedFiles':len(ledger),'qualificationOrSourceChanged':False}
(s/'live-lu-support-r1-text-compaction.json').write_bytes((json.dumps(receipt,indent=2)+'\n').encode());print(json.dumps(receipt))
