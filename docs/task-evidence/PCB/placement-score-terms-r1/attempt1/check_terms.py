"""Independent score reader; finite comparison only, no heuristic acceptance."""
import hashlib
import json
import math
from pathlib import Path
HERE = Path(__file__).resolve().parent

def need(value, message):
    if not value:
        raise ValueError(message)

def number(value):
    return type(value) in (int, float) and math.isfinite(value)

def close(actual, expected, message):
    need(number(actual) and abs(actual - expected) <= 1e-8, message)

def unique_object(pairs):
    value = {}
    for key, item in pairs:
        need(key not in value, 'Duplicate JSON field: ' + key)
        value[key] = item
    return value

def area_intersection(a, b):
    for rectangle in (a, b):
        need(set(rectangle) == {'x', 'y', 'width', 'height'} and
             all(type(v) is int for v in rectangle.values()) and
             rectangle['width'] > 0 and rectangle['height'] > 0, 'Invalid envelope')
    width = max(0, min(a['x'] + a['width'], b['x'] + b['width']) - max(a['x'], b['x']))
    height = max(0, min(a['y'] + a['height'], b['y'] + b['height']) - max(a['y'], b['y']))
    return width * height

def assess(row, binding):
    need(str(row['seed']) == binding['root'] and str(row['layoutSeed']) == binding['layoutSeed'] and
         row['attempt'] == 1 and row['densityVersion'] == 14 and row['componentId'] == 'QA', 'Replay identity')
    for name in ('rawPlanSha256', 'returnedPlanSha256', 'pinnedSelectedPlanSha256'):
        need(row[name] == binding['selectedPlanSha256'], 'Pinned selected plan: ' + name)
    need(row['selectionBindingSource'] == 'pocket-decision-cause-r1/focused-native.txt' and
         row['priorSelectionLogSha256'] == binding['priorLogSha256'] and
         row['noRoutingExecuted'] is True, 'Borrowed selection observation scope')
    need(row['sourcePlanSha256'] == '4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59' and
         row['sourceGraphSha256'] == '840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a', 'Source identities')
    need(row['sourcePlanUnchanged'] is True and row['graphUnchanged'] is True, 'Read-only source/graph')
    trace = row['trace']
    need(trace['cap'] == 60000 and trace['capped'] is False and trace['records'] == 6800, 'Existing trace boundary')
    origins = row['origins']
    need(type(origins) is list and len(origins) == trace['records'] and
         all(o['sequence'] == i for i, o in enumerate(origins)), 'Complete original origin sequence')
    context = row['context']
    need(context['pinLocal'] is True and context['anchor'] is False and context['algorithmCandidate'] == 1,
         'Existing advice predicates')
    targets = context['vacancyTargets']
    need(type(targets) is list and len(targets) <= 5 and all(set(p) == {'x', 'y'} and
         all(type(v) is int for v in p.values()) for p in targets), 'Bounded vacancy targets')
    if context['support'] is None and targets:
        connected = context['connectedTarget']
        nearest = min(targets, key=lambda p: abs(p['x'] - connected['x']) + abs(p['y'] - connected['y']))
        need(context['vacancyPreference'] == nearest, 'Independent canonical nearest preference')
    else:
        need(context['vacancyPreference'] is None, 'Suppressed/absent preference')
    if targets:
        envelope = row['chosen']['accessEnvelope']
        left = min(p['x'] for p in targets)
        top = min(p['y'] for p in targets)
        expected_pocket = {'x': left + envelope['x'] - row['chosen']['x'],
                           'y': top + envelope['y'] - row['chosen']['y'],
                           'width': max(p['x'] for p in targets) - left + envelope['width'],
                           'height': max(p['y'] for p in targets) - top + envelope['height']}
        need(context['vacancyPocket'] == expected_pocket, 'Independent pocket construction')
    else:
        need(context['vacancyPocket'] is None, 'No target pocket')
    for name in ('chosen', 'alternative'):
        value = row[name]
        origin = origins[value['sequence']]
        need(origin['fits'] is True and origin['componentId'] == 'QA' and
             all(origin[k] == value[k] for k in ('x', 'y', 'rotation', 'mountingSide', 'effectiveScore', 'loopKind')),
             'Origin-to-score observation join')
        expected = binding['chosen'] if name == 'chosen' else binding['alternative']
        need(value['x'] == expected.get('rawX', expected.get('x')) and
             value['y'] == expected.get('rawY', expected.get('y')) and
             value['rotation'] == 'DEG_0' and value['mountingSide'] == 'TOP', 'Pinned origin/pose')
        need(value['effectiveScore'] == expected.get('chosenScore', expected.get('effectiveScore')),
             'Pinned prior effective score')
        if name == 'alternative':
            need(value['sequence'] == 6297, 'Previously observed alternative sequence')
        terms = ('baseScore', 'wireDistance', 'corridorPenalty', 'wholeContentGrowth', 'groupGrowth',
                 'groupCenter', 'hint', 'supportEndpointSpan', 'vacancyDistanceCost',
                 'vacancyOverlapReward', 'effectiveScore')
        need(all(number(value[k]) for k in terms), 'Nonfinite score term')
        close(sum(value[k] for k in ('wireDistance', 'corridorPenalty', 'wholeContentGrowth',
                                    'groupGrowth', 'groupCenter', 'hint')), value['baseScore'], 'Base decomposition')
        effective = value['baseScore']
        effective += value['supportEndpointSpan']
        effective += value['vacancyDistanceCost']
        effective -= value['vacancyOverlapReward']
        need(effective == value['effectiveScore'], 'Exact ordered effective arithmetic')
        preference = context['vacancyPreference']
        distance = 0 if preference is None else (abs(value['x'] - preference['x']) + abs(value['y'] - preference['y'])) * .25
        need(value['vacancyDistanceCost'] == distance, 'Independent preference distance')
        pocket = context['vacancyPocket']
        reward = 0 if pocket is None else area_intersection(value['accessEnvelope'], pocket) / 40.0
        need(value['vacancyOverlapReward'] == reward, 'Independent overlap reward')
        if context['support'] is None:
            need(value['supportEndpointSpan'] == 0, 'Absent support has no term')
    chosen, alternate = row['chosen'], row['alternative']
    delta = {k: alternate[k] - chosen[k] for k in ('wireDistance', 'corridorPenalty', 'wholeContentGrowth',
             'groupGrowth', 'groupCenter', 'hint', 'baseScore', 'supportEndpointSpan',
             'vacancyDistanceCost', 'vacancyOverlapReward', 'effectiveScore')}
    need(delta['effectiveScore'] > 0, 'Existing chosen origin wins')
    return {'status': 'PASS', 'root': '42', 'attempt': 1, 'scoreDeltaAlternativeMinusChosen': delta,
            'claim': 'Finite existing-score decomposition only; prior raw-pocket causal status remains INCONCLUSIVE; no routing/final-shrinkage proof'}

def main():
    log = (HERE / 'native.txt').read_text(encoding='utf-8-sig')
    prefix = 'PCB_PLACEMENT_SCORE_TERMS_CASE '
    rows = [json.loads(line[len(prefix):], object_pairs_hook=unique_object)
            for line in log.splitlines() if line.startswith(prefix)]
    need(len(rows) == 1 and 'PASS: PCB placement score terms contracts: ' in log and
         'PASS: focused current contracts; 1 Java suites.' in log and
         'CLEANUP: current JVM contract classes and task-owned scratch removed.' in log, 'Native terminal proof')
    timing = json.loads((HERE / 'native-timing.json').read_text(encoding='utf-8-sig'))
    need(timing['status'] == 'PASS' and timing['exitCode'] == 0, 'Native terminal outcome')
    result = assess(rows[0], json.loads((HERE / 'plan-binding.json').read_text()))
    result['nativeLogSha256'] = hashlib.sha256((HERE / 'native.txt').read_bytes()).hexdigest()
    (HERE / 'assessment.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))

if __name__ == '__main__':
    main()
