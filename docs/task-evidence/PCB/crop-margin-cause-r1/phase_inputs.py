"""Freeze/audit consumed native and diagnostic gate inputs without host paths."""
import hashlib,importlib.util,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COLLECTOR=HERE.parent/'pad-cost-vacancy-r1/input_snapshot.py'
spec=importlib.util.spec_from_file_location('crop_input_base',COLLECTOR)
collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)
def collect():
    rows={r['path']:r['sha256'] for r in collector.collect(ROOT)}
    owners=[COLLECTOR,HERE.parent/'soft-isolation-advice-r1/published14-baselines.json']
    owners += [HERE/n for n in ('protocol.json','manage.py','assess.py','check_assessor.py','run_focused.ps1','focused-plan.json','phase_inputs.py')]
    owners += list(HERE.glob('PcbCropMarginCauseContractTest*.java'))
    for p in owners:rows[p.relative_to(ROOT).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    return [{'path':k,'sha256':rows[k]} for k in sorted(rows)]
def main():
    mode=sys.argv[1];target=HERE/'focused-inputs.json';actual=collect()
    if mode=='freeze':
        if target.exists():raise ValueError('Frozen inputs already exist')
        target.write_bytes((json.dumps({'files':actual},indent=2)+'\n').encode());print('FROZEN inputs='+str(len(actual)));return
    if mode!='audit':raise ValueError('Expected freeze/audit')
    expected=json.loads(target.read_text(encoding='utf-8-sig'))['files']
    before={r['path']:r['sha256'] for r in expected};after={r['path']:r['sha256'] for r in actual}
    changed=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    result={'status':'PASS' if not changed else 'FAIL','changedPaths':changed,'frozenInputCount':len(before),'actualInputCount':len(after),'scope':'All native source/scripts/contracts/GWT jars and explicit diagnostic gate owners'}
    (HERE/'focused-input-audit.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result));raise SystemExit(1 if changed else 0)
if __name__=='__main__':main()
