"""Summarize the already accepted observations; adds no acceptance rule."""
import collections
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
text = (HERE / 'native.txt').read_text(encoding='utf-8-sig')
events = [json.loads(s[len('PCB_ROUTED_STRIP_EVENT '):]) for s in text.splitlines() if s.startswith('PCB_ROUTED_STRIP_EVENT ')]
assessment = json.loads((HERE / 'assessment.json').read_text())
assert assessment['status'] == 'PASS'
selected = []
for finding in assessment['findings']:
    axes = [e for e in events if e['kind'] == 'AXIS' and e['seed'] == finding['seed']
        and e['callOrdinal'] in finding['matchingCallOrdinals']]
    assert len(axes) == 2 and all(a['status'] == 'NO_CUT' and a['cutCount'] == 0 for a in axes)
    selected.append({'seed': finding['seed'], 'matchingCompactionCalls': finding['matchingCallOrdinals'],
        'axes': [{k: a[k] for k in ('axis', 'status', 'occupiedIntervalCount', 'cutCount', 'cuts')} for a in axes]})
applied = [{k: e[k] for k in ('seed', 'callOrdinal', 'axis', 'cutCount', 'cuts')}
    for e in events if e['kind'] == 'AXIS' and e['status'] == 'APPLIED']
(HERE / 'selected-axis-summary.json').write_text(json.dumps({'status': 'PASS', 'selectedBoards': selected,
    'allRecordedAxisStatuses': dict(collections.Counter(e['status'] for e in events if e['kind'] == 'AXIS')),
    'otherAppliedAxes': applied,
    'conclusion': 'Both selected layouts have no eligible full-axis strip under existing guards; no selected caught rejection to fix. A repeated or partial strip pass has no measured selected opportunity here',
    'limits': 'Two informed boards only; no minimum-packing, rotation-benefit, transformed-board or population claim'}, indent=2) + '\n')
print('PASS selected X/Y NO_CUT for both cases; all records9NO_CUT/1APPLIED; no rejected axes')
