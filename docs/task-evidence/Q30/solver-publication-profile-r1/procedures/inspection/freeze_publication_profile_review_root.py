from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
d=s/'scratch/solver-publication-profile-r1-draft'
m=json.loads((d/'source-manifest.json').read_text())
overlays=[]
for name,entry in m['overlays'].items():
    raw=(d/name).read_bytes()
    digest=hashlib.sha256(raw).hexdigest()
    assert digest==entry['sha256'],name
    if name!='README.md': overlays.append({'path':name,'bytes':len(raw),'sha256':digest})
out=s/'solver-publication-profile-reviewed-inputs.json'
assert not out.exists()
out.write_text(json.dumps({'status':'ROOT_REVIEWED_SOURCE_ONLY','overlays':overlays,
 'review':'Final solver call order, private lifecycle and collector arithmetic inspected; pause/resume, duplicate finish, monotonic clock and pinned JSONBoolean API repairs verified. Native, GWT and real host gates still NOT RUN.',
 'limitations':'Accepted bookkeeping starts after t/timeStepAccum updates. Selected timings are sampled coarse costs, not exact totals. Collector freezes before verifier retirement cleanup. No source integrated.'},indent=2)+'\n')
print(hashlib.sha256(out.read_bytes()).hexdigest())
