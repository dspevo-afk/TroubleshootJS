"""Sealed source bindings and strict native passive-pose result reader."""
import hashlib
import json
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BASE_SHA = '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
ROOTS = ['-1017', '-1004', '-1022', '0', '17', '42', '1', '7', '5', '14', '2', '3', '4', '6']
PASSIVES = {'AXIAL_RESISTOR', 'AXIAL_DIODE', 'RADIAL_ELECTROLYTIC_CAPACITOR', 'RADIAL_CERAMIC_CAPACITOR'}
METRICS = ['area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias']
INPUT_FIELDS = ('seed', 'sourceCanonical15', 'sourcePlanSha25616', 'graph16',
                'intent16', 'layoutSeed16', 'routingSeed16', 'eligibleIds')
RAW_FIELDS = ('seed', 'attempt', 'geometry15', 'geometry16', 'evaluations15',
              'evaluations16', 'demand15', 'demand16', 'courtyard15', 'courtyard16')
POSE_FIELDS = ('seed', 'issuedAdmissionIdentity', 'version', 'top0', 'top90')
SAVED_FIELDS = (
    'status', 'seed', 'version', 'density', 'parts', 'pads', 'area', 'width', 'height',
    'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'geometrySha256',
    'sourcePlanSha256', 'liveBoardGraphSha256', 'sourceIntentSha256', 'layoutSeed',
    'routingSeed', 'selectedAttempt', 'rankedPlacementAttempts', 'acceptedPlanEvaluations',
    'placementRejections', 'oneFaceAttempts', 'twoLayerAttempts', 'routeAttempts',
    'routingOrders', 'routingExpansions', 'selectedQualityMilli', 'statisticsCanonical',
    'fixedMarginFloor', 'accessEnvelopeMargin', 'requiredMargin', 'legacyRequestedMargin',
    'requestedMargin', 'actualMargin', 'occupiedX', 'occupiedY', 'occupiedWidth',
    'occupiedHeight', 'outlineX', 'outlineY', 'excludedWorkbenchChrome', 'elapsedMillis')
TERMINAL = 'PASS: focused current contracts; {count} Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
TIMING_SCOPE = 'Explicit native-only passive policy prerequisite; unchanged per-JVM and search limits'
REPORT_PREFIXES = ('PCB_PASSIVE_POLICY_INPUT ', 'PCB_PASSIVE_POLICY_RAW ',
                   'PCB_PASSIVE_POSE_SOURCE_CASE ', 'PCB_PASSIVE_POLICY_ROUTE ',
                   'PCB_PASSIVE_POLICY_POSES ')
FAILURE_LINE = re.compile(
    r'^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|'
    r'AssertionError\b|BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|'
    r'CURRENT_CONTRACT_CLEANUP:)', re.IGNORECASE)


def baselines():
    path = HERE / 'accepted15-baselines.json'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == BASE_SHA, 'Sealed accepted15 baseline changed'
    document = json.loads(path.read_text(encoding='utf-8'))
    assert type(document) is dict and type(document.get('rows')) is list
    rows = document['rows']
    assert len(rows) == 14 and [r.get('seed') for r in rows] == ROOTS
    for row in rows:
        assert type(row) is dict and tuple(row.keys()) == SAVED_FIELDS, 'Saved route schema/order changed'
        assert type(row['elapsedMillis']) is int and 0 <= row['elapsedMillis'] <= 60000
    return rows


def digest(value):
    assert type(value) is str
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def long_text(value):
    assert type(value) is str and re.fullmatch(r'-?(0|[1-9][0-9]*)', value)
    assert str(int(value)) == value and -(1 << 63) <= int(value) < (1 << 63)
    return value


def input_case(row, old):
    assert type(row) is dict and tuple(row.keys()) == INPUT_FIELDS, 'Source binding schema/order changed'
    assert long_text(row['seed']) == old['seed']
    canonical = row['sourceCanonical15']
    assert type(canonical) is str and 0 < len(canonical) <= 256 * 1024
    assert digest(canonical) == old['sourcePlanSha256'], 'Unbound old source canonical'
    marker = 'placement-interpretation=15;'
    assert canonical.count(marker) == 1, 'Old source interpretation marker must be unique'
    derived = digest(canonical.replace(marker, 'placement-interpretation=16;'))
    assert type(row['sourcePlanSha25616']) is str and row['sourcePlanSha25616'] == derived, 'Unbound trial interpretation'
    assert row['graph16'] == old['liveBoardGraphSha256'] and row['intent16'] == old['sourceIntentSha256']
    assert long_text(row['layoutSeed16']) == old['layoutSeed']
    assert long_text(row['routingSeed16']) == old['routingSeed']
    parts = re.findall(r'\[([^\[\]]+)\]', canonical)
    assert len(parts) == old['parts'] and sum(len(part.split('|')) - 1 for part in parts) == old['pads']
    headers = [part.split('|')[0].split(':') for part in parts]
    assert all(len(header) == 7 for header in headers), 'Source component header schema changed'
    assert len({header[0] for header in headers}) == len(headers), 'Duplicate source component identity'
    eligible = sorted(header[0] for header in headers if header[3] in PASSIVES)
    assert type(row['eligibleIds']) is list and all(type(value) is str for value in row['eligibleIds'])
    assert row['eligibleIds'] == eligible, 'Source passive census changed'
    assert eligible == sorted(set(eligible))
    return {'seed': old['seed'], 'sourcePlanSha25616': derived, 'eligibleIds': eligible}


def inputs(rows, old):
    assert type(old) is list and len(old) == 14 and [row.get('seed') for row in old] == ROOTS
    assert type(rows) is list and len(rows) == 14, 'All14 ordered source bindings required'
    assert all(type(row) is dict for row in rows)
    assert [row.get('seed') for row in rows] == [row['seed'] for row in old], 'Source roots missing, duplicate, or reordered'
    assert len({row['seed'] for row in rows}) == 14
    bindings = [input_case(row, baseline) for row, baseline in zip(rows, old)]
    return {row['seed']: binding for row, binding in zip(old, bindings)}


def raw(rows):
    expected = [(seed, attempt) for seed in ('42', '-1022', '4') for attempt in (0, 2, 3, 4, 5)]
    assert type(rows) is list and len(rows) == 15, 'All15 untouched candidate branches required'
    assert all(type(row) is dict for row in rows)
    assert [(row.get('seed'), row.get('attempt')) for row in rows] == expected
    for row in rows:
        assert tuple(row.keys()) == RAW_FIELDS, 'Raw candidate schema/order changed'
        assert type(row['seed']) is str and type(row['attempt']) is int
        assert type(row['geometry15']) is str and re.fullmatch(r'[0-9a-f]{64}', row['geometry15'])
        assert type(row['geometry16']) is str and row['geometry15'] == row['geometry16']
        for key in ('evaluations', 'demand', 'courtyard'):
            assert type(row[key + '15']) is int and type(row[key + '16']) is int
            assert row[key + '15'] == row[key + '16'], 'Untouched candidate branch changed: ' + key
        assert 0 < row['evaluations15'] <= 60000
        assert row['demand15'] > 0 and row['courtyard15'] > 0


def route_case(row, old, version, binding):
    assert type(row) is dict and set(row) == set(old), 'Changed routed row schema'
    assert set(old) == set(SAVED_FIELDS) and len(old) == 43
    for key in old:
        assert type(row[key]) is type(old[key]), 'Changed field type: ' + key
    assert type(row['elapsedMillis']) is int and 0 <= row['elapsedMillis'] <= 60000
    assert type(row['version']) is int and row['version'] == version
    if version == 15:
        assert all(row[key] == value for key, value in old.items() if key != 'elapsedMillis'), 'Old15 route changed: ' + old['seed']
        return
    assert version == 16
    assert row['status'] == 'PASS' and row['density'] == 14
    for key in ('seed', 'parts', 'pads', 'liveBoardGraphSha256', 'sourceIntentSha256',
                'layoutSeed', 'routingSeed', 'excludedWorkbenchChrome'):
        assert row[key] == old[key], 'Trial source drift: ' + key
    assert type(binding) is dict and binding.get('seed') == old['seed']
    assert set(binding) == {'seed', 'sourcePlanSha25616', 'eligibleIds'}
    assert row['sourcePlanSha256'] == binding['sourcePlanSha25616']
    assert re.fullmatch(r'[0-9a-f]{64}', row['geometrySha256'])
    assert row['width'] > 0 and row['height'] > 0 and row['area'] == row['width'] * row['height']
    assert row['actualMargin'] >= row['requiredMargin'] >= 0
    assert row['requestedMargin'] == row['legacyRequestedMargin']
    assert 0 < row['acceptedPlanEvaluations'] <= 6 * 60000 and 0 <= row['placementRejections'] <= 6
    assert 0 <= row['oneFaceAttempts'] <= 3 and 1 <= row['twoLayerAttempts'] <= 3
    assert row['routeAttempts'] == row['oneFaceAttempts'] + row['twoLayerAttempts'] <= 6
    ranked = row['rankedPlacementAttempts']
    assert type(ranked) is list and 1 <= len(ranked) <= 6 and len(set(ranked)) == len(ranked)
    assert all(type(attempt) is int and 0 <= attempt < 6 for attempt in ranked)
    assert row['selectedAttempt'] in ranked
    assert row['selectedQualityMilli'] >= 0 and row['routingOrders'] >= 0 and row['routingExpansions'] >= 0
    assert ';outcome=SUCCESS;' in row['statisticsCanonical']
    assert ';selected=P07_FULLER_TWO_LAYER;' in row['statisticsCanonical']
    assert ';placements=6;' in row['statisticsCanonical']
    assert all(row[key] >= 0 for key in METRICS)


def pose_case(row, old, version, binding):
    assert type(row) is dict and set(row) == set(POSE_FIELDS), 'Changed pose census schema'
    assert row['seed'] == old['seed'] and type(row['version']) is int and row['version'] == version
    assert row['issuedAdmissionIdentity'] == ('RB56_BOARD_NORMAL@1' if version == 15 else 'RB56_BOARD_NORMAL@2')
    for key in ('top0', 'top90'):
        assert type(row[key]) is list and all(type(value) is str for value in row[key])
        assert row[key] == sorted(set(row[key])), 'Pose census must be ordered and unique'
    assert not set(row['top0']) & set(row['top90'])
    assert sorted(row['top0'] + row['top90']) == binding['eligibleIds'], 'Pose census lost, added, or duplicated passive IDs'
    if version == 15:
        assert row['top90'] == [], 'Saved15 passive pose census must remain TOP0-only'


def assess(rows, poses, old, bindings):
    assert type(old) is list and len(old) == 14 and [row.get('seed') for row in old] == ROOTS
    assert type(bindings) is dict and set(bindings) == set(ROOTS)
    keys = [(row['seed'], version) for version in (15, 16) for row in old]
    assert type(rows) is list and len(rows) == 28
    assert type(poses) is list and len(poses) == 28
    assert [(row.get('seed'), row.get('version')) for row in rows] == keys, 'All ordered28 routed cases required'
    assert [(row.get('seed'), row.get('version')) for row in poses] == keys, 'All ordered28 pose censuses required'
    deltas, regressions, improvements, turned = [], [], [], []
    for index, (seed, version) in enumerate(keys):
        baseline = old[index % len(old)]
        binding = bindings[seed]
        route_case(rows[index], baseline, version, binding)
        pose_case(poses[index], baseline, version, binding)
        if version == 16:
            delta = {key: rows[index][key] - baseline[key] for key in METRICS}
            deltas.append({'seed': seed, 'delta': delta})
            if any(value > 0 for value in delta.values()):
                regressions.append(seed)
            if delta['area'] < 0 or delta['largestProtectedEmptyRectangleArea'] < 0:
                improvements.append(seed)
            if poses[index]['top90']:
                turned.append(seed)
    status = 'FAIL' if regressions or not improvements else 'PASS' if turned else 'INCONCLUSIVE'
    return {'status': status, 'saved15ExactCases': len(old), 'trial16Cases': len(old),
        'regressions': regressions, 'areaOrVoidImprovements': improvements, 'selectedTurnCases': turned,
        'firstCanonicalTurnedOwnerCase': turned[0] if turned else None, 'metricDeltas': deltas,
        'limits': 'Finite native prerequisite only; turned-owner/replay/markings/GWT/player/full-matrix proof separate'}


def extract(text, prefix):
    assert type(text) is str and type(prefix) is str
    return [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]


def phase_suites_path(phase):
    assert phase in ('unit', 'routes')
    return HERE / ('unit-suites.json' if phase == 'unit' else 'route-suites.json')


def _suite_pairs(suites):
    assert type(suites) is list and suites
    pairs = []
    for item in suites:
        assert type(item) in (list, tuple) and len(item) == 2
        name, marker = item
        assert type(name) is str and re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', name)
        assert type(marker) is str and marker and '\n' not in marker and '\r' not in marker
        pairs.append((name, marker))
    assert len({name for name, _ in pairs}) == len(pairs)
    assert len({marker for _, marker in pairs}) == len(pairs)
    return pairs


def check_phase(text, receipt, timing, suites):
    """Validate one maintained native phase log, child receipt, timing, and frozen suite order."""
    assert type(text) is str and type(receipt) is str and type(timing) is dict
    pairs = _suite_pairs(suites)
    phase = timing.get('phase')
    assert phase in ('unit', 'routes')
    expected_count = 8 if phase == 'unit' else 28
    assert len(pairs) == expected_count, 'Native phase requires the frozen8/28 suite set'
    manifest = json.loads(phase_suites_path(phase).read_text(encoding='utf-8'))
    assert _suite_pairs(manifest) == pairs, 'Caller suite order differs from the frozen phase manifest'

    timing_fields = {'status', 'exitCode', 'elapsedSeconds', 'suiteCount', 'phase', 'scope',
                     'terminalCleanupIncluded', 'cleanupDuration'}
    assert set(timing) == timing_fields, 'Native timing schema changed'
    assert timing['status'] == 'PASS' and type(timing['exitCode']) is int and timing['exitCode'] == 0
    assert type(timing['suiteCount']) is int and timing['suiteCount'] == expected_count
    elapsed = timing['elapsedSeconds']
    assert type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0.0
    assert timing['terminalCleanupIncluded'] is True
    assert timing['cleanupDuration'] == 'NOT_SEPARATELY_MEASURED'
    assert timing['scope'] == TIMING_SCOPE

    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    assert not any(FAILURE_LINE.search(line) for line in text_lines + receipt_lines), 'Native phase contains failure output'
    terminal = TERMINAL.format(count=expected_count)
    assert text_lines.count(terminal) == 1, 'Expected one exact native JVM suite terminal'
    assert text_lines.count(CLEANUP) == 1, 'Maintained terminal cleanup marker missing or duplicated'
    assert terminal not in receipt_lines and CLEANUP not in receipt_lines, 'Child receipt contains outer terminal data'
    terminal_at, cleanup_at = text_lines.index(terminal), text_lines.index(CLEANUP)
    assert terminal_at < cleanup_at

    expected_passes = ['PASS: ' + marker for _, marker in pairs]
    receipt_passes = [line for line in receipt_lines if line.startswith('PASS: PCB passive pose ')]
    text_passes = [line for line in text_lines if line.startswith('PASS: PCB passive pose ')]
    assert len(receipt_passes) == expected_count and len(text_passes) == expected_count
    for expected in expected_passes:
        assert sum(line.startswith(expected) for line in receipt_lines) == 1, 'Missing/duplicate child suite PASS'
        assert sum(line.startswith(expected) for line in text_lines) == 1, 'Missing/duplicate terminal suite PASS'
    assert [any(line.startswith(expected) for expected in expected_passes) for line in receipt_passes] == [True] * expected_count
    assert all(line.startswith(expected) for line, expected in zip(receipt_passes, expected_passes)), 'Child receipt suite order changed'
    assert all(line.startswith(expected) for line, expected in zip(text_passes, expected_passes)), 'Terminal suite order changed'
    assert receipt_passes == text_passes, 'Terminal and child suite markers differ'
    assert all(text_lines.index(line) < terminal_at for line in text_passes)

    if phase == 'unit':
        report_counts = {'PCB_PASSIVE_POSE_SOURCE_CASE ': 14,
                         'PCB_PASSIVE_POLICY_INPUT ': 14, 'PCB_PASSIVE_POLICY_RAW ': 15}
    else:
        report_counts = {'PCB_PASSIVE_POLICY_ROUTE ': 28, 'PCB_PASSIVE_POLICY_POSES ': 28}
    for prefix, count in report_counts.items():
        child_rows = [line for line in receipt_lines if line.startswith(prefix)]
        terminal_rows = [line for line in text_lines if line.startswith(prefix)]
        assert len(child_rows) == count, 'Child report count changed: ' + prefix.strip()
        assert child_rows == terminal_rows, 'Terminal and child report rows differ: ' + prefix.strip()
    for prefix in set(REPORT_PREFIXES) - set(report_counts):
        assert not any(line.startswith(prefix) for line in receipt_lines + text_lines), 'Report appeared in the wrong phase: ' + prefix.strip()
    return {'status': 'PASS', 'phase': phase, 'suiteCount': expected_count,
            'suiteClasses': [name for name, _ in pairs], 'orderedMarkers': [marker for _, marker in pairs]}


if __name__ == '__main__':
    phase = 'INPUTS'
    try:
        old = baselines()
        unit = (HERE / 'unit.txt').read_text(encoding='utf-8-sig')
        routes = (HERE / 'routes.txt').read_text(encoding='utf-8-sig')
        for name, text in (('unit', unit), ('routes', routes)):
            timing = json.loads((HERE / (name + '-timing.json')).read_text(encoding='utf-8-sig'))
            receipt = (HERE / (name + '-receipt.txt')).read_text(encoding='utf-8-sig')
            suites = json.loads(phase_suites_path(name).read_text(encoding='utf-8-sig'))
            check_phase(text, receipt, timing, suites)
        bindings = inputs(extract(unit, 'PCB_PASSIVE_POLICY_INPUT '), old)
        raw(extract(unit, 'PCB_PASSIVE_POLICY_RAW '))
        phase = 'ROUTED_FITNESS'
        result = assess(extract(routes, 'PCB_PASSIVE_POLICY_ROUTE '),
                        extract(routes, 'PCB_PASSIVE_POLICY_POSES '), old, bindings)
    except (AssertionError, KeyError, TypeError, ValueError, FileNotFoundError) as failure:
        if isinstance(failure, FileNotFoundError):
            missing = getattr(failure, 'filename', None)
            reason = 'required evidence file missing' + (': ' + Path(missing).name if missing else '')
        else:
            reason = str(failure)
        result = {'status': 'FAIL', 'phase': phase, 'failureType': type(failure).__name__,
            'reason': reason, 'policyPromotion': False}
    (HERE / 'assessment.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(result['status'] + ' strict complete14+14 policy assessment')
    sys.exit(0 if result['status'] == 'PASS' else 1)
