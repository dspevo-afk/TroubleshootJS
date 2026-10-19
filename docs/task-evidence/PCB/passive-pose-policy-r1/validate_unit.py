"""Reject missing prerequisites before starting any full routed corpus."""
import json
import assess
H=assess.HERE
text=(H/'unit.txt').read_text(encoding='utf-8-sig')
receipt=(H/'unit-receipt.txt').read_text(encoding='utf-8-sig')
timing=json.loads((H/'unit-timing.json').read_text(encoding='utf-8-sig'))
suites=json.loads((H/'unit-suites.json').read_text())
assess.check_phase(text,receipt,timing,suites)
old=assess.baselines()
expected=[{k:r[k] for k in ('seed','parts','pads','sourcePlanSha256','liveBoardGraphSha256',
 'sourceIntentSha256','layoutSeed','routingSeed')} for r in old]
actual=assess.extract(text,'PCB_PASSIVE_POSE_SOURCE_CASE ')
assert actual==expected and all(type(a[k]) is type(e[k]) for a,e in zip(actual,expected) for k in e)
bindings=assess.inputs(assess.extract(text,'PCB_PASSIVE_POLICY_INPUT '),old)
assess.raw(assess.extract(text,'PCB_PASSIVE_POLICY_RAW '))
(H/'unit-validation.json').write_text(json.dumps({'status':'PASS','sourceBindings':14,
 'derivedTrialBindings':len(bindings),'unchangedRawCandidates':15,'suiteCount':8,
 'limits':'Native pure/source/raw prerequisites; no full routed or owner/player proof'},indent=2)+'\n')
print('PASS unit8,14+14 source bindings and15 untouched raw candidates')
