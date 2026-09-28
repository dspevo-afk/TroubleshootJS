from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;sha=lambda b:hashlib.sha256(b).hexdigest()
root=s/'live-lu-support-r1-preparation';auditraw=(root/'source-audit.json').read_bytes();audit=json.loads(auditraw)
assert sha(auditraw)=='ce1b55ca0b463746b19976871a5be1401972c294bf0cc881070bdd36284b7ba4'
for e in audit['armInputs']['candidate']:
    raw=(root/'candidate'/e['path']).read_bytes();assert (len(raw),sha(raw))==(e['bytes'],e['sha256']),e['path']
review=(s/'scratch/live-lu-support-r1-root-wiring-review.md').read_bytes()
assert b'This resolves finding 1' in review
checks={}
for name in ['live-lu-support-r2-native9.json','live-lu-support-r1-gwt5.json','live-lu-support-r1-canaries-summary.json','live-lu-zero-sign-witness-r1-native.json']:
    raw=(s/name).read_bytes();d=json.loads(raw);assert d['status']=='PASS' and d['exitCode']==0
    checks[name]=sha(raw)
host=json.loads((s/'live-lu-support-r1-canaries/result.json').read_bytes())
assert host['outcome']=='PASS' and host['cleanup']['status']=='PASS' and not host['cleanup']['errors'] and not host['cleanup']['ownedSurvivors']
assert 'three negative corruption canaries' in (s/'live-lu-support-r1-a07-reader.log').read_text(encoding='utf-8')
result={'status':'PASS_BOUNDED_SOURCE_REVIEW','sourceAuditSha256':sha(auditraw),'sourceInputs':len(audit['armInputs']['candidate']),'independentWiringReviewSha256':sha(review),'rootTrackerReview':'Checked monotone row/column union and mapped stamps, alias rejection at setup, identity/shape matching, per-factor rebind, sorted cross/same-group swaps, consumed column prefix, empty/nonempty/exception cleanup, no row-content writes. Root empty-clear guard cannot retain references because liveN becomes nonzero before any row-reference population. Unknown owner/shape/unbalanced support and local-zero pivot use existing full LU; internal impossible domain corruption fails through finally cleanup.','arithmeticReview':'Global pivot/last tie, row swaps and selected lower slot repair, reciprocal/scaling/underflow, upper/update order and finite guards preserved. General full path and inversion retain full scan. No family, seed, proof, solver-step, routing, cache, deadline, corpus or acceptance changes.','oracleBoundary':'Current full-kernel factor/pivot/solve comparisons include all zero signs exactly. Historical dense factor numeric equivalence ignores only zero sign after separate unchanged-baseline witness; independent nonzero bits and existing LuFactorizationChecks unchanged.','gateHashes':checks,'runtimeInputAudit':host['inputAudit'],'limits':'Static owner/kernel review and focused native/compiled gates only. No timing or optimization acceptance. 512 eligibility boundary not directly exercised; large/unsupported shapes fall back.'}
p=s/'live-lu-support-r1-source-review.json';assert not p.exists();p.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':result['status'],'sourceInputs':result['sourceInputs'],'runtimeInputs':host['inputAudit'].get('fileCountBefore'),'reviewSha256':sha(p.read_bytes())}))
