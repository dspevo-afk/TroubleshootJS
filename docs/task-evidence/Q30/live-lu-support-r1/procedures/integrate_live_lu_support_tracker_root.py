from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;sha=lambda b:hashlib.sha256(b).hexdigest()
draft=s/'scratch/live-lu-support-r1-tracker-draft';candidate=s/'live-lu-support-r1-preparation/candidate'
mraw=(draft/'manifest.json').read_bytes();assert sha(mraw)=='920be48861e2546dfe6dc79b3ca1c527e7e9d86640c90d31ce685d22123e2001'
for e in json.loads(mraw)['files']:
    raw=(draft/e['path']).read_bytes();assert sha(raw)==e['sha256']
    if e['path'].endswith('.java'):
        p=candidate/e['path']
        if p.exists():assert p.read_bytes()==raw, e['path']
        else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
p=candidate/'src/com/lushprojects/circuitjs1/client/LuStructuralSupport.java'
b=p.read_bytes();nl=b'\r\n' if b.count(b'\r\n')==b.count(b'\n') else b'\n'
old=nl.join([b'        void clear() {',b'            if (rowReferences != null) {'])
new=nl.join([b'        void clear() {',b'            // reset and preparation also clear; an empty workspace owns no rows.',b'            if (liveN == 0) {',b'                active = false;',b'                return;',b'            }',b'            if (rowReferences != null) {'])
assert b.count(old)==1;b=b.replace(old,new)
old=b'            return allZero(counts) && allZero(starts) && allZero(columnCounts);'
new=b'            return !active && liveN == 0 && allZero(counts) && allZero(starts) && allZero(columnCounts);'
assert b.count(old)==1;p.write_bytes(b.replace(old,new))
p=candidate/'src/com/lushprojects/circuitjs1/client/CirSim.java';b=p.read_bytes();nl=b'\r\n' if b'\r\n' in b else b'\n'
old=b'                if (restricted) domains.swap(k, largestRow);'
new=nl.join([b'                if (restricted) {',b'                    domains.swap(k, largestRow);',b'                    if (!domains.active)',b'                        throw new IllegalStateException("Invalid LU structural row permutation");',b'                }'])
assert b.count(old)==1;b=b.replace(old,new)
old=b'            if (restricted) domains.finishPivot(k);'
new=nl.join([b'            if (restricted) {',b'                domains.finishPivot(k);',b'                if (!domains.active)',b'                    throw new IllegalStateException("Invalid LU structural pivot domain");',b'            }'])
assert b.count(old)==1;p.write_bytes(b.replace(old,new))
result={'status':'ISOLATED_TRACKER_INTEGRATED_NOT_RUN','workerManifestSha256':sha(mraw),'rootChanges':['Idempotent empty Domains.clear avoids repeated clearing; liveN set only after empty arrays allocated, before references retained. Nonempty/partial preparation still clears full capacity.','Cleanup predicate additionally requires inactive and liveN zero.','Unexpected internal row-domain invalidation fails explicitly with existing factor finally cleanup; it cannot silently continue a restricted scan. Owner/shape rejection and zero-pivot fallback retain original full LU.'],'sourceSha256':sha((candidate/'src/com/lushprojects/circuitjs1/client/LuStructuralSupport.java').read_bytes())}
out=s/'live-lu-support-r1-tracker-integration.json';assert not out.exists();out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
