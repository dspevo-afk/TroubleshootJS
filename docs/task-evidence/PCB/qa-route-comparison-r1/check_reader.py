"""Synthetic reader boundary checks; not native route evidence."""
import copy
import importlib.util
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('qa_route_reader', HERE / 'check_routes.py')
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
accepted = json.loads((HERE / 'accepted-case.json').read_text())['case']
rows = []
for variant in ('ORIGINAL', 'TRANSLATED'):
    original = variant == 'ORIGINAL'
    source = {k: accepted[k] for k in ('seed', 'version', 'density', 'layoutSeed', 'routingSeed', 'sourcePlanSha256')}
    source.update({'sourceGraphSha256': accepted['liveBoardGraphSha256'],
                   'sourcePlanUnchanged': True, 'sourceGraphUnchanged': True})
    geometry = {k: accepted[k] for k in ('parts', 'pads', 'geometrySha256', 'area', 'width', 'height',
         'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'selectedQualityMilli')}
    plan_digest = 'd88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b'
    before = {'componentId': 'QA', 'x': 1690, 'y': 1580, 'width': 100, 'height': 100,
        'rotation': 'DEG_0', 'mountingSide': 'TOP', 'packageId': 'SYNTHETIC_QA',
        'geometryVariantKey': 'default', 'geometryTransformKey': 'DEG0/TOP', 'geometryContractVersion': 1,
        'pads': [{'padId': 'QA.SYNTHETIC_P1', 'componentId': 'QA', 'terminalId': 'SYNTHETIC_T1',
            'netId': 'SYNTHETIC_NET', 'x': 1700, 'y': 1590, 'escapeDx': 1, 'escapeDy': 0,
            'escapeLength': 10, 'attachment': 'SYNTHETIC', 'mountingSide': 'TOP', 'exposure': 'SYNTHETIC',
            'padBounds': {'x': 1698, 'y': 1588, 'width': 4, 'height': 4},
            'probeBounds': {'x': 1695, 'y': 1585, 'width': 10, 'height': 10}}]}
    after = copy.deepcopy(before)
    if not original:
        after['x'] -= 300
        after['y'] -= 30
        for pad in after['pads']:
            pad['x'] -= 300
            pad['y'] -= 30
            for name in ('padBounds', 'probeBounds'):
                pad[name]['x'] -= 300
                pad[name]['y'] -= 30
    row = {'schema': 'pcb-qa-route-comparison-r1', 'variant': variant, 'physicalStatus': 'SUCCESS',
        'source': source, 'placement': {'requestedAttempt': 1, 'internalPlannerAlgorithm': 0,
            'plannerCalls': 1, 'originalPlanSha256': plan_digest,
            'suppliedPlanSha256': plan_digest if original else '1' * 64, 'suppliedPlanIsOriginal': original,
            'candidateCount': 1, 'candidateAttemptsSupplied': [1],
            'candidateOrderMeaning': 'single supplied fixture plan; not historical rank',
            'qaOnlyTranslated': not original, 'qaBefore': before,
            'qaAfter': after,
            'allOther53FootprintsExact': True, 'qaStableComponentPadNetBindings': True, 'planMetadataUnchanged': True},
        'route': {'policy': 'P07_FULLER_TWO_LAYER', 'attempt': 1, 'routeAttempts': 1, 'p05Attempts': 0,
            'p07Attempts': 1, 'routeOutcomes': ['P07_FULLER_TWO_LAYER@1=SUCCESS'], 'orderings': 1,
            'placementCandidates': 1, 'placementEvaluations': 6800, 'rankedPlacementAttempts': [],
            'expansions': 10, 'routePhaseElapsedMillis': 100,
            'limits': {'orderings': 3, 'expansions': 1000000, 'branchExpansions': 100000,
                       'gridCells': 250000, 'workQuantum': 32768}}, 'geometry': geometry, 'rejectionReason': None}
    rows.append(row)
assert reader.assess(copy.deepcopy(rows), accepted)['status'] == 'PASS'
rejected = copy.deepcopy(rows)
rejected[1].update({'physicalStatus': 'REJECTED', 'geometry': None, 'rejectionReason': 'NO_PATH'})
rejected[1]['route']['routeOutcomes'] = ['P07_FULLER_TWO_LAYER@1=REJECTED:NO_PATH']
assert reader.assess(rejected, accepted)['status'] == 'PASS'
negative = []
def reject(index, path, value, base=None):
    candidate = copy.deepcopy(rows if base is None else base)
    target = candidate[index]
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value
    try:
        reader.assess(candidate, accepted)
    except (ValueError, KeyError, TypeError, IndexError):
        negative.append(str(index) + '/' + '.'.join(map(str, path)))
        return
    raise AssertionError('Reader accepted invalid ' + str(index) + '/' + '.'.join(map(str, path)))
for index in (0, 1):
    for path, value in ((['schema'], 'unknown'), (['source', 'seed'], '43'),
        (['source', 'layoutSeed'], 5526715536693200536), (['source', 'routingSeed'], '4493419427469983'),
        (['source', 'sourcePlanSha256'], '0' * 64), (['source', 'sourceGraphSha256'], '0' * 64),
        (['source', 'sourcePlanUnchanged'], False), (['source', 'sourceGraphUnchanged'], False),
        (['placement', 'requestedAttempt'], True), (['placement', 'internalPlannerAlgorithm'], 1),
        (['placement', 'plannerCalls'], 2), (['placement', 'candidateCount'], 6),
        (['placement', 'candidateAttemptsSupplied'], [1, 2]), (['placement', 'candidateOrderMeaning'], 'historical rank'),
        (['placement', 'originalPlanSha256'], '0' * 64), (['placement', 'allOther53FootprintsExact'], False),
        (['placement', 'qaStableComponentPadNetBindings'], False), (['placement', 'planMetadataUnchanged'], False),
        (['route', 'policy'], 'P05_ONE_FACE'), (['route', 'attempt'], 3), (['route', 'p05Attempts'], 1),
        (['route', 'p07Attempts'], 2), (['route', 'orderings'], 4), (['route', 'expansions'], 1000001),
        (['route', 'placementCandidates'], 6), (['route', 'placementEvaluations'], 6801),
        (['route', 'rankedPlacementAttempts'], [1, 2, 3, 4, 0, 5]),
        (['route', 'routePhaseElapsedMillis'], -1), (['route', 'limits', 'expansions'], 1000001),
        (['geometry', 'parts'], 53), (['geometry', 'vias'], 49)):
        reject(index, path, value)
reject(0, ['physicalStatus'], 'REJECTED', rejected)
reject(1, ['placement', 'qaOnlyTranslated'], False)
reject(1, ['placement', 'qaAfter'], rows[0]['placement']['qaAfter'])
reject(1, ['placement', 'suppliedPlanIsOriginal'], True)
reject(1, ['placement', 'suppliedPlanSha256'], rows[0]['placement']['originalPlanSha256'])
for index in (0, 1):
    for path, value in ((['placement', 'qaAfter', 'pads', 0, 'x'], 1701),
        (['placement', 'qaAfter', 'pads', 0, 'netId'], 'DIFFERENT_NET'),
        (['placement', 'qaAfter', 'pads', 0, 'terminalId'], 'DIFFERENT_TERMINAL'),
        (['placement', 'qaAfter', 'pads', 0, 'escapeLength'], 11),
        (['placement', 'qaAfter', 'pads', 0, 'probeBounds', 'width'], 11),
        (['placement', 'qaAfter', 'geometryVariantKey'], 'OTHER_VARIANT')):
        reject(index, path, value)
for field in ('geometrySha256', 'area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'selectedQualityMilli'):
    value = '0' * 64 if field == 'geometrySha256' else accepted[field] + 1
    reject(0, ['geometry', field], value)
reject(1, ['geometry'], rows[0]['geometry'], rejected)
reject(1, ['rejectionReason'], '', rejected)
try:
    json.loads('{"a":1,"a":2}', object_pairs_hook=reader.unique_object)
except ValueError:
    negative.append('duplicateJsonKey')
else:
    raise AssertionError('Duplicate field accepted')
result = {'status': 'PASS', 'positiveCases': 2, 'negativeCases': len(negative),
          'rejectedMutations': negative, 'scope': 'Synthetic reader only; physical rejection remains REJECTED'}
(HERE / 'reader-canaries.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS route reader synthetic2 positive/' + str(len(negative)) + ' negative')
