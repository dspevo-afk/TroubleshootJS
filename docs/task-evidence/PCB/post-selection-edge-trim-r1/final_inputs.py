"""Freeze promoted production and maintained final-gate owners separately from trial."""
import hashlib,importlib.util,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
source=HERE.parent/'pad-cost-vacancy-r1/input_snapshot.py'
spec=importlib.util.spec_from_file_location('edge_final_base',source)
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
def collect():
    rows={r['path']:r['sha256'] for r in base.collect(ROOT)}
    gate_owners=[source,HERE.parent/'soft-isolation-advice-r1/assess_geometry.py',
        HERE.parent/'soft-isolation-advice-r1/published14-baselines.json']
    for p in gate_owners+[HERE/n for n in ('final_inputs.py','run_final.ps1','final-suite-plan.json','promote.py','assess_geometry.py','final_assess.py')]:
        rows[p.relative_to(ROOT).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    return [{'path':p,'sha256':rows[p]} for p in sorted(rows)]
mode=sys.argv[1];target=HERE/'final-inputs.json';actual=collect()
if mode=='freeze':
    if target.exists():raise ValueError('Already frozen')
    target.write_text(json.dumps({'files':actual},indent=2)+'\n');print('FROZEN '+str(len(actual)))
elif mode=='audit':
    before={r['path']:r['sha256'] for r in json.loads(target.read_text())['files']}
    after={r['path']:r['sha256'] for r in actual}
    changed=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    result={'status':'FAIL' if changed else 'PASS','changedPaths':changed,'frozenInputCount':len(before),'actualInputCount':len(after)}
    (HERE/sys.argv[2]).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));raise SystemExit(bool(changed))
else:raise ValueError('Expected freeze/audit')
