"""Negative canaries for metric acceptance only, without generator output."""
import copy
import json
import runpy
import tempfile
from pathlib import Path
here=Path(__file__).resolve().parent
assess=runpy.run_path(str(here/'verify_measurements.py'))['assess']
baseline=json.loads((here/'published13-baselines.json').read_text())
rows=[dict(seed=c['seed'],version=14,density=13,area=c['area'],
    largestProtectedEmptyRectangleArea=c['largestProtectedEmptyRectangle']['area'],
    copperLength=c['copperLength'],vias=c['vias']) for c in baseline['cases']]
results=[]
with tempfile.TemporaryDirectory(prefix='tsj-private14-assessor-') as scratch:
    log=Path(scratch)/'metric.txt'
    def check(name,items,expected):
        log.write_text(''.join('PCB_PAD_COST_VACANCY_CASE '+json.dumps(r)+'\n' for r in items))
        try: actual=assess(baseline,[log])['status']
        except ValueError: actual='REJECT'
        if actual!=expected: raise AssertionError((name,actual,expected))
        results.append(dict(case=name,status='PASS',actual=actual))
    check('unchanged is not acceptance',rows,'FAIL')
    better=copy.deepcopy(rows);better[5]['area']-=1
    check('one strict improvement with no regression',better,'PASS')
    regress=copy.deepcopy(better);regress[0]['largestProtectedEmptyRectangleArea']+=1
    check('regression wins over improvement elsewhere',regress,'FAIL')
    check('missing root',better[:-1],'REJECT')
    check('duplicate root',better+[better[0]],'REJECT')
    extra=copy.deepcopy(better[0]);extra['seed']='999'
    check('unexpected root',better+[extra],'REJECT')
    wrong=copy.deepcopy(better);wrong[0]['version']=13
    check('wrong interpretation',wrong,'REJECT')
    wrong=copy.deepcopy(better);wrong[0]['density']=12
    check('wrong density',wrong,'REJECT')
result=dict(status='PASS',scope='Synthetic metric-assessor boundary only; no generator or physical admission claim',scratchCleanup='TemporaryDirectory completed',cases=results)
(here/'assessor-guards.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS assessor negative canaries='+str(len(results)))
