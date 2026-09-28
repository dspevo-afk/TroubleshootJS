from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
base=s/'scratch/pivot-lower-fusion-r2-preparation'
old=(base/'source-audit.json').read_bytes()
assert sha(old)=='0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197'
records=json.loads(old)['armInputs']['candidate'];assert len(records)==1340
prep=s/'live-lu-support-r1-preparation';assert not prep.exists()
candidate=prep/'candidate';candidate.mkdir(parents=True)
for e in records:
    raw=(base/'candidate'/e['path']).read_bytes()
    assert (len(raw),sha(raw))==(e['bytes'],e['sha256'])
    p=candidate/e['path'];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
(prep/'baseline-inputs.json').write_text(json.dumps({'baselineAuditSha256':sha(old),'inputs':records,'generatedWarCopied':False,'status':'BASELINE_COPIED_ONLY'},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'copiedInputs':len(records),'status':'BASELINE_COPIED_ONLY','generatedWarCopied':False}))
