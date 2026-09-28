from pathlib import Path
import gzip,hashlib,json,sys
p=Path(sys.argv[1]).resolve();scratch=Path(sys.argv[2]).resolve()
assert p.name=='lu-support-census-r4' and p.parent.name=='Q30'
assert p.is_dir() and scratch.is_dir()
sha=lambda b:hashlib.sha256(b).hexdigest()
def encoded(x):return (json.dumps(x,indent=2,ensure_ascii=False)+'\n').encode()
def owned(path):
    assert path.resolve().is_relative_to(p)
    for part in [path]+list(path.parents):
        assert not part.is_symlink() and not part.is_junction(),part
        if part==p:break
    return path
invraw=(p/'inventory.json').read_bytes();assert sha(invraw)=='a71f8ea5b27b2dd91cbcfa6f07e77655d2ece8f7df6d67b5c2de3115566dbcd0'
prior=scratch/'census-r4-pre-compaction-inventory-a71f8ea5.json';assert not prior.exists();prior.write_bytes(invraw)
inv=json.loads(invraw);rows=inv['artifacts'];compact=[]
for e in rows:
    f=owned(p/e['path']);raw=f.read_bytes();assert (len(raw),sha(raw))==(e['storedBytes'],e['storedSha256'])
    if f.suffix not in ('.json','.log') or len(raw)<262144:continue
    target=owned(Path(str(f)+'.gz'));assert not target.exists()
    packed=gzip.compress(raw,compresslevel=9,mtime=0);assert gzip.decompress(packed)==raw
    target.write_bytes(packed)
    oldpath=e['path'];e['path']+='.gz'
    e['storedBytes']=len(packed);e['storedSha256']=sha(packed)
    e['payloadBytes']=len(raw);e['payloadSha256']=sha(raw)
    e['transform']='gzip-mtime-0;'+e['transform']
    compact.append({'path':e['path'],'restorePath':oldpath,'payloadBytes':len(raw),'payloadSha256':sha(raw),'storedBytes':len(packed),'storedSha256':sha(packed),'gzipMtimeZero':True})
    # Exact individually verified task-created payload, safely retained in gzip.
    assert f.read_bytes()==raw and gzip.decompress(target.read_bytes())==raw
    f.unlink()
def generated_update(rel,raw,kind):
    f=owned(p/rel);f.write_bytes(raw)
    e=next(e for e in rows if e['path']==rel)
    e.update(sourceRef='generated:compact_lu_support_census_packet_root.py',sourceBytes=len(raw),sourceSha256=sha(raw),storedBytes=len(raw),storedSha256=sha(raw),transform=kind)
ledger=json.loads((p/'provenance/compression-ledger.json').read_bytes())
ledger['artifacts']+=compact
ledger['largePayloadCompaction']={'priorInventorySha256':sha(invraw),'thresholdBytes':262144,'payloadsPreservedExactly':True,'reason':'Large structured proof and comparison receipts are data; retain exact payloads while keeping the reviewed diff bounded.'}
generated_update('provenance/compression-ledger.json',encoded(ledger),'generated-compression-ledger')
readme=(p/'README.md').read_text(encoding='utf-8').rstrip()+'\n\nLarge JSON/log receipts are stored as deterministic gzip with unchanged decompressed bytes. The compression ledger records each restore path and payload hash; decompress and remove the final `.gz` suffix to restore those files. The original uncompressed inventory is retained in task scratch under its recorded hash.\n'
generated_update('README.md',readme.encode(),'generated-evidence-summary')
script=Path(__file__).resolve();raw=script.read_bytes();rel='procedures/inspection/'+script.name
f=owned(p/rel);assert not f.exists();f.write_bytes(raw)
rows.append({'path':rel,'sourceRef':'scratch/'+script.name,'sourceBytes':len(raw),'sourceSha256':sha(raw),'storedBytes':len(raw),'storedSha256':sha(raw),'transform':'exact'})
rows.sort(key=lambda e:e['path']);inv['artifactCountExcludingInventory']=len(rows)
inv['priorInventorySha256']=sha(invraw)
newraw=encoded(inv);(p/'inventory.json').write_bytes(newraw);(p/'inventory.sha256').write_text(sha(newraw)+'  inventory.json\n',encoding='ascii')
summary={'status':'EXACT_LARGE_PAYLOAD_COMPACTION_PENDING_ROOT_AUDIT','priorInventorySha256':sha(invraw),'inventorySha256':sha(newraw),'artifacts':len(rows),'compressedArtifacts':len(compact),'payloadBytes':sum(e['payloadBytes'] for e in compact),'compressedBytes':sum(e['storedBytes'] for e in compact),'qualificationOrSourceChanged':False}
out=scratch/'lu-support-census-r4-compaction.json';assert not out.exists();out.write_bytes(encoded(summary));print(json.dumps(summary))
