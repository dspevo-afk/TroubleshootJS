from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;sha=lambda b:hashlib.sha256(b).hexdigest()
gate=json.loads((s/'live-lu-zero-sign-witness-r1-native.json').read_bytes());assert gate['status']=='PASS' and gate['exitCode']==0
assert 'unchanged accepted full LU [3][2] rawBits=0' in (s/'live-lu-zero-sign-witness-r1-native.log').read_text(encoding='utf-8')
root=s/'live-lu-support-r1-preparation';rel='tests/contracts/LuStructuralFactorizationContractTest.java'
p=root/'candidate'/rel;raw=p.read_bytes();auditraw=(root/'source-audit.json').read_bytes()
assert sha(auditraw)=='1a3acd40ac5478f1993e2564b29e5536ebc2a6ae80a3295d1bf9f96983126ede'
prior=s/'live-lu-support-r1-before-zero-sign-correction';assert not prior.exists();prior.mkdir()
(prior/'LuStructuralFactorizationContractTest.java').write_bytes(raw)
(prior/'source-audit.json').write_bytes(auditraw)
old=b'check(sameBits(oracle[i][j], result.factors[i][j]),'
new=b'check(sameBits(oracle[i][j], result.factors[i][j]) ||\n                        (oracle[i][j] == 0.0 && result.factors[i][j] == 0.0),'
assert raw.count(old)==1;updated=raw.replace(old,new)
old=b'label + ": independent factor raw bits differ at ["'
new=b'label + ": independent nonzero factor bits differ at ["'
assert updated.count(old)==1;updated=updated.replace(old,new)
needle=b'    private static void independentOracle(double[][] matrix, double[] rhs,'
comment=b'    // The unchanged accepted zero-product LU preserves untouched zero signs;\n    // the historical dense Crout loop can create -0.0 (pinned by the separate\n    // accepted-baseline witness). Nonzero factor bits remain independent here.\n    // comparePrepared retains exact full-path factor/pivot/solve bits, including zeros.\n'
assert updated.count(needle)==1;p.write_bytes(updated.replace(needle,comment+needle))
audit=json.loads(auditraw)
for e in audit['armInputs']['candidate']:
    if e['path']==rel:e['bytes']=p.stat().st_size;e['sha256']=sha(p.read_bytes())
audit['previousSourceAuditSha256']=sha(auditraw)
audit['oracleCorrection']='Only new historical dense-factor comparison treats both zero signs as the same numerical zero. Independent nonzero factor bits and all current full-path factor/pivot/solve bits remain exact. Unchanged accepted-source native witness PASS23 demonstrates inherited difference. Existing LuFactorizationChecks source and assertions unchanged.'
(root/'source-audit.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
helper=s/'prepare_live_lu_support_measurement_root.py';b=helper.read_bytes();old=b'live-lu-support-r1-native9.json';assert b.count(old)==2;helper.write_bytes(b.replace(old,b'live-lu-support-r2-native9.json'))
out={'status':'NEW_TEST_ORACLE_CORRECTED_TO_ACCEPTED_CONTRACT_PENDING_GATE','beforeTestSha256':sha(raw),'afterTestSha256':sha(p.read_bytes()),'sourceAuditSha256':sha((root/'source-audit.json').read_bytes()),'productionSourceChanged':False,'baselineWitnessAssertions':23,'retainedFailure':'live-lu-support-r1-native9.json','existingDenseOracleChanged':False,'fullCurrentKernelBitParityWeakened':False}
(s/'live-lu-support-r2-oracle-correction.json').write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8');print(json.dumps(out))
