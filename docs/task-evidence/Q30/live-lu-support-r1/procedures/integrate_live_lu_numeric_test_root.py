from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;sha=lambda b:hashlib.sha256(b).hexdigest()
rel='tests/contracts/LuStructuralFactorizationContractTest.java'
src=s/'scratch/live-lu-support-r1-numeric-test'/rel
b=src.read_bytes();assert sha(b)=='d82b7b8a8a179bcbf2821242fe01c334a138fede01d7b5f7f22616fde30861f7'
p=s/'live-lu-support-r1-preparation/candidate'/rel;assert not p.exists()
nl=b'\r\n' if b.count(b'\r\n')==b.count(b'\n') else b'\n'
old=nl.join([b'        if (n >= 2 && allRowsNonzero)',b'            check(matches(support, actual, supportOriginal, n, mapping),',b'                    label + ": balanced multi-domain support was not eligible");'])
new=nl.join([b'        if (n >= 2 && allRowsNonzero) {',b'            check(matches(support, actual, supportOriginal, n, mapping),',b'                    label + ": fresh support was not bound to this factor");',b'            LuStructuralSupport.Domains eligibility = new LuStructuralSupport.Domains();',b'            check(((LuStructuralSupport)support).prepareDomains(eligibility, actual,',b'                    supportOriginal, n, mapping) && eligibility.active,',b'                    label + ": balanced multi-domain support was not eligible");',b'            eligibility.clear();',b'            check(eligibility.allCountsZeroForChecks() && eligibility.allReferencesNullForChecks(),',b'                    label + ": eligibility probe retained scratch");',b'        }'])
assert b.count(old)==1;p.write_bytes(b.replace(old,new))
candidate=s/'live-lu-support-r1-preparation/candidate'
base=json.loads((s/'live-lu-support-r1-preparation/baseline-inputs.json').read_bytes())['inputs']
records=[]
for f in sorted(candidate.rglob('*')):
    if f.is_file():
        raw=f.read_bytes();records.append({'path':f.relative_to(candidate).as_posix(),'bytes':len(raw),'sha256':sha(raw)})
assert len(records)==1343,len(records)
before={e['path']:e['sha256'] for e in base}
changed=[e['path'] for e in records if before.get(e['path'])!=e['sha256']]
expected=['scripts/verify-current-contracts.ps1','src/com/lushprojects/circuitjs1/client/CirSim.java','src/com/lushprojects/circuitjs1/client/LuStructuralSupport.java','src/com/lushprojects/circuitjs1/client/Task41SimulationSnapshot.java','tests/contracts/LuStructuralFactorizationContractTest.java','tests/contracts/LuStructuralSupportContractTest.java']
assert changed==expected,changed
audit={'status':'ISOLATED_SOURCE_READY_FOR_FOCUSED_GATES','baselineAuditSha256':'0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197','workerNumericTestSha256':sha(b),'numericEligibilityRootChange':'Explicitly require balanced multi-domain preparation; matching owner alone is insufficient. Probe scratch fully cleared.','changedInputs':changed,'armInputs':{'control':base,'candidate':records},'limits':'No qualification, timing or numerical acceptance yet. No generated WAR copied.'}
p=s/'live-lu-support-r1-preparation/source-audit.json';assert not p.exists();p.write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'sourceInputs':len(records),'sourceAuditSha256':sha(p.read_bytes()),'changedInputs':changed}))
