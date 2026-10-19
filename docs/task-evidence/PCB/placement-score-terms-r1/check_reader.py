"""Synthetic reader canaries, separate from native generator evidence."""
import copy
import importlib.util
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('score_reader', HERE / 'check_terms.py')
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
binding = json.loads((HERE / 'plan-binding.json').read_text())
digest = binding['selectedPlanSha256']
row = {'seed': '42', 'layoutSeed': binding['layoutSeed'], 'attempt': 1, 'densityVersion': 14,
       'componentId': 'QA', 'sourcePlanUnchanged': True, 'graphUnchanged': True,
       'sourcePlanSha256': '4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59',
       'sourceGraphSha256': '840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a',
       'rawPlanSha256': digest, 'returnedPlanSha256': digest, 'pinnedSelectedPlanSha256': digest,
       'selectionBindingSource': 'pocket-decision-cause-r1/focused-native.txt', 'noRoutingExecuted': True,
       'priorSelectionLogSha256': binding['priorLogSha256'],
       'trace': {'cap': 60000, 'capped': False, 'records': 6800},
       'context': {'pinLocal': True, 'anchor': False, 'algorithmCandidate': 0,
                   'support': None, 'vacancyTargets': [], 'connectedTarget': {'x': 0, 'y': 0},
                   'vacancyPreference': None, 'vacancyPocket': None}}
for name in ('chosen', 'alternative'):
    source = binding[name]
    value = {k: 0.0 for k in ('baseScore', 'wireDistance', 'corridorPenalty', 'wholeContentGrowth',
             'groupGrowth', 'groupCenter', 'hint', 'supportEndpointSpan', 'vacancyDistanceCost',
             'vacancyOverlapReward')}
    score = source.get('chosenScore', source.get('effectiveScore'))
    value.update({'x': source.get('rawX', source.get('x')), 'y': source.get('rawY', source.get('y')),
                  'rotation': 'DEG_0', 'mountingSide': 'TOP', 'loopKind': 'NORMAL',
                  'sequence': 6297 if name == 'alternative' else 1,
                  'baseScore': score, 'wireDistance': score, 'effectiveScore': score,
                  'accessEnvelope': {'x': 0, 'y': 0, 'width': 10, 'height': 10}})
    row[name] = value
row['origins'] = [{'sequence': i} for i in range(6800)]
for name in ('chosen', 'alternative'):
    row['origins'][row[name]['sequence']] = dict(row[name], fits=True, componentId='QA')
assert reader.assess(copy.deepcopy(row), binding)['status'] == 'PASS'
negative = []
def reject(path, value, base=None):
    candidate = copy.deepcopy(row if base is None else base)
    target = candidate
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value
    try:
        reader.assess(candidate, binding)
    except (ValueError, KeyError, TypeError):
        negative.append('.'.join(path))
        return
    raise AssertionError('Reader accepted invalid ' + '.'.join(path))
for path, value in ((['seed'], '43'), (['layoutSeed'], '5526715536693200537'),
                    (['attempt'], 0), (['densityVersion'], 13), (['componentId'], 'QB'),
                    (['sourcePlanUnchanged'], False), (['graphUnchanged'], False),
                    (['sourcePlanSha256'], '0' * 64), (['sourceGraphSha256'], '0' * 64),
                    (['rawPlanSha256'], '0' * 64), (['returnedPlanSha256'], '0' * 64),
                    (['pinnedSelectedPlanSha256'], '0' * 64), (['selectionBindingSource'], 'unknown'),
                    (['priorSelectionLogSha256'], '0' * 64),
                    (['noRoutingExecuted'], False), (['trace', 'cap'], 60001),
                    (['trace', 'capped'], True), (['trace', 'records'], 6801),
                    (['context', 'pinLocal'], False), (['context', 'anchor'], True),
                    (['context', 'algorithmCandidate'], 1), (['alternative', 'sequence'], 6298)):
    reject(path, value)
for name in ('chosen', 'alternative'):
    for key, value in (('x', 1), ('y', 1), ('rotation', 'DEG_90'), ('mountingSide', 'BOTTOM'),
                       ('baseScore', 1), ('wireDistance', float('nan')), ('corridorPenalty', 1),
                       ('supportEndpointSpan', 1), ('vacancyDistanceCost', 1),
                       ('vacancyOverlapReward', 1), ('effectiveScore', 1)):
        reject([name, key], value)
advised = copy.deepcopy(row)
advised['context'].update({'vacancyTargets': [{'x': 1390, 'y': 1550}],
    'vacancyPreference': {'x': 1390, 'y': 1550},
    'vacancyPocket': {'x': 1390, 'y': 1550, 'width': 10, 'height': 10}})
for name in ('chosen', 'alternative'):
    value = advised[name]
    value['accessEnvelope'] = {'x': value['x'], 'y': value['y'], 'width': 10, 'height': 10}
    value['vacancyDistanceCost'] = 82.5 if name == 'chosen' else 0.0
    value['vacancyOverlapReward'] = 0.0 if name == 'chosen' else 2.5
    value['baseScore'] = value['effectiveScore'] - value['vacancyDistanceCost'] + value['vacancyOverlapReward']
    value['wireDistance'] = value['baseScore']
assert reader.assess(advised, binding)['status'] == 'PASS'
for path, value in ((['context', 'vacancyPreference'], {'x': 0, 'y': 0}),
                    (['context', 'vacancyTargets'], [{'x': 1390, 'y': 1550}] * 6),
                    (['context', 'vacancyPocket'], {'x': 1390, 'y': 1550, 'width': 11, 'height': 10}),
                    (['chosen', 'vacancyDistanceCost'], 82.0),
                    (['alternative', 'vacancyOverlapReward'], 2.0)):
    reject(path, value, advised)
try:
    json.loads('{"a":1,"a":1}', object_pairs_hook=reader.unique_object)
except ValueError:
    negative.append('duplicateJsonKey')
else:
    raise AssertionError('Duplicate JSON accepted')
result = {'status': 'PASS', 'positiveCases': 2, 'negativeCases': len(negative),
          'rejectedMutations': negative, 'scope': 'Synthetic independent-reader checks only; no native/placement evidence'}
(HERE / 'reader-canaries.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS reader synthetic 2 positive/' + str(len(negative)) + ' negative')
