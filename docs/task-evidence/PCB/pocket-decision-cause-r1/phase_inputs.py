"""Freeze diagnostic source, immutable accepted oracles and native/reader owners."""
import hashlib,importlib.util,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
base_file=HERE.parent/'pad-cost-vacancy-r1/input_snapshot.py'
spec=importlib.util.spec_from_file_location('pocket_input_base',base_file)
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
def collect():
    name='PcbPocketDecisionDiagnosticContractTest'
    active=ROOT/'tests/contracts'/str(name+'.java')
    archive=HERE/(name+'.java')
    if active.read_bytes()!=archive.read_bytes():raise ValueError('Active/archive source mismatch')
    registry=(ROOT/'scripts/verify-current-contracts.ps1').read_text()
    if registry.count("Name = '"+name+"';")!=1:raise ValueError('Expected unique suite')
    rows={r['path']:r['sha256'] for r in base.collect(ROOT)}
    for row in json.loads((HERE/'diagnostic-source.json').read_text())['files']:
        if rows[row['path']]!=row['sha256']:raise ValueError('Recorded diagnostic owner changed '+row['path'])
    owners=[base_file,HERE.parent/'post-selection-edge-trim-r1/final-native.txt']
    owners += [HERE/n for n in ('proposal.json','pre-runtime-clarification.json','accepted15-cases.json','original-source.json',
        'diagnostic-source.json','focused-plan.json','manage.py','run_focused.ps1',
        'phase_inputs.py','assess_decisions.py','check_assessor.py',name+'.java',
        'planner-instrumentation.java','planner-instrumentation.patch')]
    for p in owners:rows[p.relative_to(ROOT).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    return [{'path':p,'sha256':rows[p]} for p in sorted(rows)]
mode=sys.argv[1];target=HERE/'focused-inputs.json';actual=collect()
if mode=='freeze':
    if target.exists():raise ValueError('Already frozen')
    target.write_text(json.dumps({'files':actual},indent=2)+'\n');print('FROZEN inputs='+str(len(actual)))
elif mode=='audit':
    before={r['path']:r['sha256'] for r in json.loads(target.read_text())['files']}
    after={r['path']:r['sha256'] for r in actual}
    changed=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    result={'status':'FAIL' if changed else 'PASS','changedPaths':changed,'frozenInputCount':len(before),'actualInputCount':len(after)}
    (HERE/'focused-input-audit.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result));raise SystemExit(bool(changed))
else:raise ValueError('Expected freeze/audit')
