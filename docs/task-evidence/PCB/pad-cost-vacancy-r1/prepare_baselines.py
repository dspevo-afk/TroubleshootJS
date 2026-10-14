"""Extract immutable accepted13 receipts; no current generator execution."""
import hashlib
import json
from pathlib import Path
here = Path(__file__).resolve().parent
prior = here.parent / 'nearest-vacancy-preference-r1'
source = (prior / 'final-native.txt').read_bytes()
records, whitespace = {}, {}
for line in source.decode('utf-8-sig').splitlines():
    if line.startswith('PCB_NEAREST_VACANCY_CASE '):
        item = json.loads(line.split(' ', 1)[1])
        if item['seed'] in records: raise ValueError('duplicate baseline')
        records[item['seed']] = item
    elif line.startswith('PCB_WHITESPACE_CASE '):
        item = json.loads(line.split(' ', 1)[1])
        if item['version'] == 13:
            if item['seed'] in whitespace: raise ValueError('duplicate baseline void')
            whitespace[item['seed']] = item
expected = ['-1017','-1004','-1022','0','17','42','1','7','5','14']
if set(records) != set(expected) or set(whitespace) != set(expected):
    raise ValueError('incomplete baseline')
cases=[]
for seed in expected:
    item, void = records[seed], whitespace[seed]
    if item['version'] != 13 or item['density'] != 12: raise ValueError('wrong baseline')
    cases.append(dict(seed=seed, area=item['area'], copperLength=item['copperLength'],
        vias=item['vias'], segments=item['segments'], geometrySha256=item['geometrySha256'],
        parts=void['parts'], protectedFreeCells=void['protectedFreeCells'],
        largestProtectedEmptyRectangle=void['largestProtectedEmptyRectangle']))
result=dict(status='FROZEN_PUBLISHED13_BASELINES',
    commit='96bd1b1414866733927f93bdf0b039bd09cae204', sourceLogSha256=hashlib.sha256(source).hexdigest(),
    scope='Six representatives and four untuned roots, accepted final49 receipts before private14',cases=cases)
(here/'published13-baselines.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print('FROZEN accepted13 baseline cases='+str(len(cases)))
