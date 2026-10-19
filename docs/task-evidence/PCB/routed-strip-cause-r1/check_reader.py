"""Synthetic observer reader canaries before any native cause observation."""
import copy
import hashlib
import json
from pathlib import Path
import assess as reader
import final_rows
HERE = Path(__file__).resolve().parent
rows = final_rows.baselines()
events, bindings = [], []
for index, seed in enumerate(final_rows.ROOTS):
    sig = ('a' if index == 0 else 'b') * 64
    proposed = 'c' * 64
    call = {'schema': 'PCB_ROUTED_STRIP_EVENT@1', 'seed': seed, 'kind': 'CALL',
        'callOrdinal': 1, 'status': 'COMPLETE', 'inputSignature': sig,
        'outputSignature': sig, 'axisCount': 2, 'truncated': False}
    axis = {'schema': 'PCB_ROUTED_STRIP_EVENT@1', 'seed': seed, 'kind': 'AXIS',
        'callOrdinal': 1, 'axisOrdinal': 0, 'axis': 'X', 'status': 'PLACEMENT_REJECTED',
        'occupiedIntervalCount': 20, 'cutCount': 1, 'cutsTruncated': False,
        'cuts': [[100, 30]], 'inputSignature': sig, 'proposedSignature': proposed,
        'outputSignature': sig, 'caughtType': 'com.lushprojects.circuitjs1.client.PcbPlacementPlanner$Rejected',
        'caughtMessage': 'literal isolated-domain guard'}
    y = dict(axis, axisOrdinal=1, axis='Y', status='NO_CUT', cutCount=0,
        cuts=[], proposedSignature=None, caughtType=None, caughtMessage=None)
    events.extend([call, axis, y])
    bindings.append({'schema': 'PCB_ROUTED_STRIP_BINDING@1', 'seed': seed,
        'selectedSignature': sig, 'matchingCallOrdinals': [1],
        'incompleteCallOrdinals': [], 'truncated': False, 'status': 'MATCHED'})
assert reader.assess(rows, events, bindings)['status'] == 'PASS'
applied = copy.deepcopy(events)
applied[1].update(status='APPLIED', proposedSignature=applied[1]['outputSignature'],
    caughtType=None, caughtMessage=None)
assert reader.assess(rows, applied, bindings)['status'] == 'PASS'
truncated = copy.deepcopy(bindings)
truncated[0].update(truncated=True, status='INCONCLUSIVE')
assert reader.assess(rows, events, truncated)['status'] == 'INCONCLUSIVE'
ambiguous_events = copy.deepcopy(events)
second_call = copy.deepcopy(events[:3])
for event in second_call:
    event['callOrdinal'] = 2
ambiguous_events.extend(second_call)
ambiguous_bindings = copy.deepcopy(bindings)
ambiguous_bindings[0].update(status='AMBIGUOUS', matchingCallOrdinals=[1, 2])
assert reader.assess(rows, ambiguous_events, ambiguous_bindings)['status'] == 'INCONCLUSIVE'
incomplete_events = copy.deepcopy(events)
incomplete_events[0].update(status='INCOMPLETE', outputSignature=None, axisCount=1)
incomplete_events[1].update(status='PENDING', outputSignature=None, caughtType=None, caughtMessage=None)
incomplete_events.pop(2)
incomplete_bindings = copy.deepcopy(bindings)
incomplete_bindings[0].update(status='INCONCLUSIVE', matchingCallOrdinals=[], incompleteCallOrdinals=[1])
assert reader.assess(rows, incomplete_events, incomplete_bindings)['status'] == 'INCONCLUSIVE'
no_match = copy.deepcopy(bindings)
no_match[0].update(status='NO_MATCH', selectedSignature='d' * 64, matchingCallOrdinals=[])
assert reader.assess(rows, events, no_match)['status'] == 'INCONCLUSIVE'
cut_truncation = copy.deepcopy(events)
cut_truncation[0]['truncated'] = True
cut_truncation[1].update(cutCount=257, cutsTruncated=True, cuts=[[100 + i * 50, 10] for i in range(256)])
assert reader.assess(rows, cut_truncation, truncated)['status'] == 'INCONCLUSIVE'
negative = []


def reject(label, change):
    r, e, b = copy.deepcopy(rows), copy.deepcopy(events), copy.deepcopy(bindings)
    change(r, e, b)
    try:
        reader.assess(r, e, b)
    except (AssertionError, KeyError, TypeError, ValueError):
        negative.append(label)
        return
    raise AssertionError(label + ' unexpectedly accepted')


reject('missing-final-row', lambda r, e, b: r.pop())
reject('missing-binding', lambda r, e, b: b.pop())
reject('duplicate-binding', lambda r, e, b: b.__setitem__(1, copy.deepcopy(b[0])))
reject('missing-call', lambda r, e, b: e.pop(0))
reject('duplicate-axis', lambda r, e, b: e.append(copy.deepcopy(e[1])))
reject('orphan-axis', lambda r, e, b: e[1].__setitem__('callOrdinal', 2))
for key, value in [('seed', '9'), ('schema', 'bad'), ('kind', 'UNKNOWN'),
                   ('callOrdinal', 0), ('callOrdinal', True), ('status', 'UNKNOWN'),
                   ('axisCount', 3), ('axisCount', True), ('truncated', 1),
                   ('inputSignature', 'bad'), ('outputSignature', 'd' * 64)]:
    reject('bad-call-' + key, lambda r, e, b, k=key, v=value: e[0].__setitem__(k, v))
for key, value in [('schema', 'bad'), ('axisOrdinal', 1), ('axisOrdinal', True),
                   ('axis', 'Y'), ('status', 'UNKNOWN'), ('occupiedIntervalCount', 0),
                   ('occupiedIntervalCount', True), ('cutCount', -1), ('cutCount', 2),
                   ('cutsTruncated', True), ('cutsTruncated', 1), ('cuts', [[100, 0]]),
                   ('cuts', [[100, 31]]), ('cuts', [[True, 30]]),
                   ('inputSignature', 'd' * 64), ('proposedSignature', None),
                   ('outputSignature', 'd' * 64), ('caughtType', 'OTHER'),
                   ('caughtMessage', None), ('caughtMessage', '')]:
    reject('bad-axis-' + key + '-' + str(value), lambda r, e, b, k=key, v=value: e[1].__setitem__(k, v))
for key, value in [('schema', 'bad'), ('status', 'AMBIGUOUS'), ('status', 'NO_MATCH'),
                   ('selectedSignature', 'bad'), ('matchingCallOrdinals', []),
                   ('matchingCallOrdinals', [1, 1]), ('matchingCallOrdinals', [True]),
                   ('incompleteCallOrdinals', [1]), ('truncated', 1)]:
    reject('bad-binding-' + key + '-' + str(value), lambda r, e, b, k=key, v=value: b[0].__setitem__(k, v))
(HERE / 'reader-canaries.json').write_text(json.dumps({'status': 'PASS',
    'phase': 'Before native observer runtime/observations', 'positiveCases': 2,
    'explicitInconclusiveCases': 5, 'negativeCases': len(negative), 'negativeLabels': negative,
    'readerSha256': hashlib.sha256((HERE / 'assess.py').read_bytes()).hexdigest(),
    'scope': 'Synthetic schema/binding/cause reader only; native fixture/physical checks separate'}, indent=2) + '\n')
print('PASS observer reader2positive/5inconclusive/' + str(len(negative)) + 'negative canaries')
