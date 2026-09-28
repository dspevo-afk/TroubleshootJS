from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
base=s/'scratch/pivot-lower-fusion-r2-preparation'
auditraw=(base/'source-audit.json').read_bytes()
assert sha(auditraw)=='0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197'
records=json.loads(auditraw)['armInputs']['candidate']
assert len(records)==1340
reviewraw=(s/'solver-publication-profile-reviewed-inputs.json').read_bytes()
review=json.loads(reviewraw)
assert review['status']=='ROOT_REVIEWED_SOURCE_ONLY'
draft=s/'scratch/solver-publication-profile-r1-draft'
expected=review['overlays']
assert len(expected)==4
for e in expected:
    raw=(draft/e['path']).read_bytes()
    assert (len(raw),sha(raw))==(e['bytes'],e['sha256']),e['path']
prep=s/'solver-publication-profile-r1-preparation'
assert not prep.exists()
candidate=prep/'candidate'
candidate.mkdir(parents=True)
control=base/'candidate'
for e in records:
    raw=(control/e['path']).read_bytes()
    assert (len(raw),sha(raw))==(e['bytes'],e['sha256']),e['path']
    to=candidate/e['path'];to.parent.mkdir(parents=True,exist_ok=True);to.write_bytes(raw)
for e in expected:
    to=candidate/e['path'];to.parent.mkdir(parents=True,exist_ok=True)
    to.write_bytes((draft/e['path']).read_bytes())
driver='scripts/verify-current-contracts.ps1'
raw=(candidate/driver).read_bytes()
needle=b"        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },"
assert raw.count(needle)==1
ending=b'\r\n' if b'\r\n' in raw else b'\n'
addition=b"        @{ Name = 'Q30SolverPublicationProfileContractTest'; Marker = 'Q30 solver publication profile contracts ' },"
(candidate/driver).write_bytes(raw.replace(needle,needle+ending+addition))
inputs=[]
for path in sorted(candidate.rglob('*')):
    if path.is_file():
        raw=path.read_bytes();inputs.append({'path':path.relative_to(candidate).as_posix(),'bytes':len(raw),'sha256':sha(raw)})
before={e['path']:e['sha256'] for e in records}
changed=[e['path'] for e in inputs if before.get(e['path'])!=e['sha256']]
assert set(changed)=={e['path'] for e in expected}|{driver}
assert len(inputs)==1342 and sum(e['path'].startswith('war/') for e in inputs)==29
receipt={'status':'SOURCE_ONLY_PREPARED_NOT_VALIDATED','baselineAuditSha256':sha(auditraw),
         'reviewedInputsSha256':sha(reviewraw),'controlSource':str(control),'candidateSource':str(candidate),
         'changedInputs':changed,'generatedWarCopied':False,'armInputs':{'control':records,'candidate':inputs}}
raw=(json.dumps(receipt,indent=2)+'\n').encode()
(prep/'source-audit.json').write_bytes(raw)
(s/'solver-publication-profile-source-pair-path.txt').write_bytes(str(prep).encode())
print(json.dumps({'status':receipt['status'],'sourceAuditSha256':sha(raw),'candidateInputs':len(inputs),'changedInputs':changed}))
