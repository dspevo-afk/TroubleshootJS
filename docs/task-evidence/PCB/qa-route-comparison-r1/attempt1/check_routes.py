"""Independent finite supplied-plan reader, never production policy acceptance."""
import hashlib
import copy
import json
import re
from pathlib import Path
HERE = Path(__file__).resolve().parent

def need(ok, message):
    if not ok:
        raise ValueError(message)

def integer(value, low, high, message):
    need(type(value) is int and low <= value <= high, message)

def digest(value):
    need(type(value) is str and re.fullmatch('[0-9a-f]{64}', value) is not None, 'Digest syntax')

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'Duplicate JSON field: ' + key)
        result[key] = value
    return result

def check_qa_binding(before, after, translated):
    need(before['componentId'] == after['componentId'] == 'QA', 'QA component identity')
    need((before['x'], before['y'], before['rotation'], before['mountingSide']) ==
         (1690, 1580, 'DEG_0', 'TOP'), 'Pinned original QA pose')
    integer(before['width'], 1, 16000, 'QA width')
    integer(before['height'], 1, 16000, 'QA height')
    need(type(before['pads']) is list and 0 < len(before['pads']) <= 131 and
         len({p['padId'] for p in before['pads']}) == len(before['pads']), 'QA pad inventory')
    dx, dy = (-300, -30) if translated else (0, 0)
    expected = copy.deepcopy(before)
    expected['x'] += dx
    expected['y'] += dy
    for pad in expected['pads']:
        need(pad['componentId'] == 'QA' and type(pad['terminalId']) is str and pad['terminalId'] and
             type(pad['netId']) is str and pad['netId'], 'Authoritative QA pad mapping')
        pad['x'] += dx
        pad['y'] += dy
        for key in ('padBounds', 'probeBounds'):
            rectangle = pad[key]
            need(set(rectangle) == {'x', 'y', 'width', 'height'} and
                 all(type(v) is int for v in rectangle.values()) and
                 rectangle['width'] > 0 and rectangle['height'] > 0, 'QA pad/probe rectangle')
            rectangle['x'] += dx
            rectangle['y'] += dy
    need(after == expected, 'Independent whole-QA stable metadata/pad/probe translation')

def assess(rows, accepted):
    need(len(rows) == 2 and [r['variant'] for r in rows] == ['ORIGINAL', 'TRANSLATED'], 'Ordered fixed pair')
    plan_digest = 'd88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b'
    need(accepted['seed'] == '42' and accepted['version'] == 15 and accepted['density'] == 14 and
         accepted['layoutSeed'] == '5526715536693200536' and accepted['routingSeed'] == '4493419427463069983', 'Pinned accepted source')
    caps = {'orderings': 3, 'expansions': 1000000, 'branchExpansions': 100000,
            'gridCells': 250000, 'workQuantum': 32768}
    summaries = []
    for row in rows:
        original = row['variant'] == 'ORIGINAL'
        need(row['schema'] == 'pcb-qa-route-comparison-r1', 'Record schema')
        s = row['source']
        for field in ('seed', 'version', 'density', 'layoutSeed', 'routingSeed', 'sourcePlanSha256'):
            need(s[field] == accepted[field], 'Accepted source field: ' + field)
        need(s['sourceGraphSha256'] == accepted['liveBoardGraphSha256'] and
             s['sourcePlanUnchanged'] is True and s['sourceGraphUnchanged'] is True, 'Read-only electrical/source boundary')
        p = row['placement']
        for name in ('requestedAttempt', 'plannerCalls', 'candidateCount'):
            integer(p[name], 1, 1, 'Single supplied integer: ' + name)
        integer(p['internalPlannerAlgorithm'], 0, 0, 'Internal algorithm integer')
        need(p['requestedAttempt'] == 1 and p['internalPlannerAlgorithm'] == 0 and
             p['plannerCalls'] == 1 and p['candidateCount'] == 1 and p['candidateAttemptsSupplied'] == [1], 'Single supplied attempt')
        need(all(type(v) is int for v in p['candidateAttemptsSupplied']), 'Exact supplied attempt transport')
        need(p['candidateOrderMeaning'] == 'single supplied fixture plan; not historical rank', 'Historical rank disclosure')
        need(p['originalPlanSha256'] == plan_digest, 'Strict original plan binding')
        digest(p['suppliedPlanSha256'])
        need((p['suppliedPlanSha256'] == plan_digest) == original and p['suppliedPlanIsOriginal'] is original, 'Supplied plan identity')
        check_qa_binding(p['qaBefore'], p['qaAfter'], not original)
        need(p['qaOnlyTranslated'] is (not original), 'Pinned whole-QA input translation')
        need(p['allOther53FootprintsExact'] is True and p['qaStableComponentPadNetBindings'] is True and
             p['planMetadataUnchanged'] is True, 'Input footprint/metadata invariants')
        r = row['route']
        for name in ('attempt', 'routeAttempts', 'p07Attempts'):
            integer(r[name], 1, 1, 'Single route integer: ' + name)
        integer(r['p05Attempts'], 0, 0, 'P05 not executed')
        need(r['policy'] == 'P07_FULLER_TWO_LAYER' and r['attempt'] == 1 and
             r['routeAttempts'] == r['p07Attempts'] == 1 and r['p05Attempts'] == 0, 'One actual P07 path')
        need(r['placementCandidates'] == 1 and r['placementEvaluations'] == 6800 and
             r['rankedPlacementAttempts'] == [], 'Truthful supplied-plan work without historical ranking')
        need(r['limits'] == caps and all(type(v) is int for v in r['limits'].values()), 'Unchanged router limits')
        integer(r['orderings'], 1, 3, 'Bounded route orderings')
        integer(r['expansions'], 0, 1000000, 'Bounded route expansions')
        integer(r['routePhaseElapsedMillis'], 0, 60000, 'Route-phase timing within bounded JVM')
        need(type(r['routeOutcomes']) is list and len(r['routeOutcomes']) == 1, 'Single route outcome')
        status = row['physicalStatus']
        need(status in ('SUCCESS', 'REJECTED') and (not original or status == 'SUCCESS'), 'Original positive baseline')
        if status == 'SUCCESS':
            need(row['rejectionReason'] is None and r['routeOutcomes'] == ['P07_FULLER_TWO_LAYER@1=SUCCESS'], 'Successful route identity')
            g = row['geometry']
            need(type(g) is dict and g['parts'] == 54 and g['pads'] == 131, 'Physical inventory')
            digest(g['geometrySha256'])
            for key in ('area', 'width', 'height', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'selectedQualityMilli'):
                integer(g[key], 0, (1 << 63) - 1, 'Physical metric: ' + key)
            need(0 < g['width'] <= 16000 and 0 < g['height'] <= 16000 and
                 g['area'] == g['width'] * g['height'] <= 64000000 and
                 g['largestProtectedEmptyRectangleArea'] <= g['area'], 'Physical metric consistency')
            integer(g['vias'], 0, 48, 'P07 via bound')
            if original:
                for key in ('parts', 'pads', 'geometrySha256', 'area', 'width', 'height',
                            'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'selectedQualityMilli'):
                    need(g[key] == accepted[key], 'Exact accepted original output: ' + key)
        else:
            need(row['geometry'] is None and type(row['rejectionReason']) is str and row['rejectionReason'], 'Typed rejection is not geometry proof')
            need(r['routeOutcomes'][0].startswith('P07_FULLER_TWO_LAYER@1=REJECTED:'), 'Rejected route identity')
        summaries.append({'variant': row['variant'], 'physicalStatus': status,
                          'orderings': r['orderings'], 'expansions': r['expansions'],
                          'routePhaseElapsedMillis': r['routePhaseElapsedMillis'],
                          'geometry': row['geometry'], 'rejectionReason': row['rejectionReason']})
    result = {'status': 'PASS', 'scope': 'Independent finite measurement reader only',
              'originalExactlyAccepted': True, 'cases': summaries, 'productionHeuristicAccepted': False,
              'limits': 'One supplied P07 plan per case; no historical six-candidate ranking/P05 replay, electrical/player/full-matrix/general qualification'}
    need(rows[0]['placement']['qaBefore'] == rows[1]['placement']['qaBefore'], 'Same original QA across independent cases')
    if rows[1]['physicalStatus'] == 'SUCCESS':
        result['translatedMinusOriginal'] = {k: rows[1]['geometry'][k] - rows[0]['geometry'][k]
            for k in ('area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'selectedQualityMilli')}
    return result

def main():
    raw = (HERE / 'native.txt').read_bytes()
    text = raw.decode('utf-8-sig')
    prefix = 'PCB_QA_ROUTE_CASE '
    rows = [json.loads(s[len(prefix):], object_pairs_hook=unique_object) for s in text.splitlines() if s.startswith(prefix)]
    for marker in ('PASS: PCB QA route original contracts: ', 'PASS: PCB QA route translated contracts: ',
                   'PASS: focused current contracts; 2 Java suites.',
                   'CLEANUP: current JVM contract classes and task-owned scratch removed.'):
        need(marker in text, 'Missing qualified terminal marker')
    timing = json.loads((HERE / 'native-timing.json').read_text(encoding='utf-8-sig'))
    need(timing['status'] == 'PASS' and timing['exitCode'] == 0, 'Completed native driver')
    result = assess(rows, json.loads((HERE / 'accepted-case.json').read_text())['case'])
    result['nativeLogSha256'] = hashlib.sha256(raw).hexdigest()
    (HERE / 'assessment.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))

if __name__ == '__main__':
    main()
