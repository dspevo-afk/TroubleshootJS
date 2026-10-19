"""Freeze/audit all existing source inputs plus this phase's declared gate owners."""
import hashlib,importlib.util,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
collector_path=HERE.parent/'pad-cost-vacancy-r1/input_snapshot.py'
spec=importlib.util.spec_from_file_location('route_input_base',collector_path)
collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)
def collect():
    rows={r['path']:r['sha256'] for r in collector.collect(ROOT)}
    for path in [collector_path]+[HERE/name for name in ('phase_inputs.py','assess.py','check_assessor.py','manage.py','run_focused.ps1','focused-plan.json','protocol.json')]:
        rows[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    old=HERE.parent/'soft-isolation-advice-r1'
    for path in list(HERE.glob('PcbRouteRankingCauseContractTest*.java'))+[old/name for name in ('apply_trial.py','original-source.json','byte-restoration.json','published14-baselines.json','focused-native.txt','rejected-source/PcbSoftIsolationAdviceContractTest.java')]:
        rows[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    return [dict(path=k,sha256=rows[k]) for k in sorted(rows)]
def main():
    mode=sys.argv[1];target=HERE/'focused-inputs.json';actual=collect()
    if mode=='freeze':
        if target.exists():raise ValueError('Do not overwrite frozen phase inputs')
        target.write_bytes((json.dumps(dict(files=actual),indent=2)+'\n').encode('utf-8'))
        print('FROZEN phase inputs='+str(len(actual)));return
    if mode!='audit':raise ValueError('Expected freeze/audit')
    before={r['path']:r['sha256'] for r in json.loads(target.read_text(encoding='utf-8-sig'))['files']}
    after={r['path']:r['sha256'] for r in actual}
    changed=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    result=dict(status='PASS' if not changed else 'FAIL',changedPaths=changed,frozenInputCount=len(before),actualInputCount=len(after),scope='Source/scripts/all registered Java contracts/GWT jars and declared phase gate owners')
    (HERE/'focused-input-audit.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print(json.dumps(result));raise SystemExit(1 if changed else 0)
if __name__=='__main__':main()
