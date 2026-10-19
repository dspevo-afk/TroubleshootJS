"""Independent reader for the sealed passive sizing-control protocol."""
import hashlib
import importlib.util
import json
import math
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRIOR_ASSESS_SHA256 = 'c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26'
PUBLISHED_POLICY_UNIT_SHA256 = 'e58c5e72aea79e65efcb427a643310545742a5161d83072b54fccdb0397f9570'
PINNED_DOCUMENTS = {
    'proposal.json': '4c42f270f063d931d5180b184f049137cbf6ad0681c776a89783930cbcd08c4d',
    'protocol.json': 'a1e0859389d3890d813f70ba91f19922893e750cfd334ac1a88c51c2583cb1a6',
    'unit-suites.json': '829d8bea0a3fe4b6ecb79bd74f3019b5623b66c7bfd028321786dc00e6f17897',
    'route-suites.json': '8498271b32e5a521819ced0ce9288f1d3f6c1c4935bd65c9fa1f3e2fd8f30e59',
    'accepted15-baselines.json': '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816',
    'route-baselines.json': 'aa17f3137be2b929d2bcaf3651640b34a7db511010b5d4a05c1f813799013b19',
    'raw-baselines.json': 'c896f2ee0cb553220f5e7a4814e6ae8b7aecc21a2f7c787803db42e8a76f1790',
    'pose-baselines.json': '377f70b7c888722ac1600b8d446cebb4230f7ab9ba78b1fccf8782cfdfa71ce8',
}
METRICS = ('area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias')
TIMING_FIELDS = {
    'status', 'exitCode', 'elapsedSeconds', 'suiteCount', 'phase', 'scope',
    'terminalCleanupIncluded', 'cleanupDuration'
}
TERMINAL = 'PASS: focused current contracts; {count} Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
FAILURE_LINE = re.compile(
    r'^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|TIMEOUT\b|Timed out\b|'
    r'Exception in thread\b|AssertionError\b|BUILD FAILED\b|\[ERROR\]|'
    r'CURRENT_CONTRACT_FAILURE:|CURRENT_CONTRACT_CLEANUP:)', re.IGNORECASE)
SUITE_PASS_PREFIX = 'PASS: PCB passive '
_DETAIL_TOKEN = (
    r'(?:assertions=[1-9][0-9]*|[1-9][0-9]* assertions|'
    r'(?:geometries|sequence|visits|roots|cases|seed|attempts|candidates|routes|rows|records|'
    r'sourceCases|routeCases|sourceRows|routeRows|candidateRows|rawCandidates|oldCases|trialCases|'
    r'controlCases|evaluations|selected|unchanged|version)='
    r'[-A-Za-z0-9][A-Za-z0-9._,/+:-]{0,63})')
SUITE_DETAIL = re.compile(_DETAIL_TOKEN + r'(?: ' + _DETAIL_TOKEN + r'){0,11}')
_PRIOR = None


def _sha256(data):
    return hashlib.sha256(data).hexdigest()


def _read_json(name):
    path = HERE / name
    data = path.read_bytes()
    expected = PINNED_DOCUMENTS.get(name)
    assert expected is not None, 'Unpinned evidence document: ' + name
    assert _sha256(data) == expected, 'Pinned evidence changed: ' + name
    return json.loads(data.decode('utf-8-sig'))


def _protocol():
    doc = _read_json('protocol.json')
    assert type(doc) is dict and doc.get('schema') == 'pcb-passive-sizing-control-v1'
    assert doc.get('cases') == [['42', 15], ['42', 16], ['42', 17], ['3', 15], ['3', 16], ['3', 17]]
    assert doc.get('admissionByVersion') == {
        '15': 'RB56_BOARD_NORMAL@1', '16': 'RB56_BOARD_NORMAL@2', '17': 'RB56_BOARD_NORMAL@3'}
    assert doc.get('policyByVersion') == {'15': 0, '16': 1, '17': 2}
    assert type(doc.get('density')) is int and doc['density'] == 14
    assert doc.get('sourcePrefixes') == {
        'old': 'PCB_PASSIVE_POSE_SOURCE_CASE ',
        'trial16': 'PCB_PASSIVE_POLICY_INPUT ',
        'control17': 'PCB_SIZING_CONTROL_INPUT '}
    assert doc.get('controlSourceFields') == [
        'seed', 'sourceCanonical15', 'sourcePlanSha25617', 'graph17', 'intent17',
        'layoutSeed17', 'routingSeed17', 'eligibleIds', 'densityVersion17',
        'passivePosePolicy17', 'admissionIdentity17']
    assert doc.get('rawPrefix') == 'PCB_SIZING_CONTROL_RAW '
    assert doc.get('rawFields') == [
        'seed', 'attempt', 'geometry15', 'geometry17', 'evaluations15', 'evaluations17',
        'demand15', 'demand17', 'courtyard15', 'courtyard17']
    assert doc.get('rawCases') == [
        [seed, attempt] for seed in ('42', '-1022', '4') for attempt in (0, 2, 3, 4, 5)]
    assert doc.get('routePrefix') == 'PCB_SIZING_CONTROL_ROUTE '
    assert doc.get('posePrefix') == 'PCB_SIZING_CONTROL_POSES '
    assert doc.get('poseFields') == ['seed', 'issuedAdmissionIdentity', 'version', 'top0', 'top90']
    assert doc.get('unitReportOrder') == 'old14,trial16source14,control17source14,raw15; pure policy/admission emit only PASS markers'
    assert doc.get('routeReportOrder') == 'pose,route per case;42 at15/16/17 then3 at15/16/17'
    assert doc.get('timingScope') == 'Native sizing-only control; unchanged per-JVM/search/physical limits'
    expected_baselines = {name: digest for name, digest in PINNED_DOCUMENTS.items()
                          if name.endswith('-baselines.json')}
    assert doc.get('baselinesSha256') == expected_baselines
    return doc


def _prior_reader():
    global _PRIOR
    if _PRIOR is not None:
        return _PRIOR
    path = HERE.parent / 'passive-pose-policy-r1' / 'assess.py'
    data = path.read_bytes()
    assert _sha256(data) == PRIOR_ASSESS_SHA256, 'Pinned prior source reader changed'
    spec = importlib.util.spec_from_file_location('_pinned_passive_pose_policy_assess', path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    _PRIOR = module
    return _PRIOR


def _accepted_rows():
    prior = _prior_reader()
    doc = _read_json('accepted15-baselines.json')
    assert type(doc) is dict and type(doc.get('rows')) is list
    rows = doc['rows']
    assert len(rows) == 14 and [row.get('seed') for row in rows] == prior.ROOTS
    for row in rows:
        assert type(row) is dict and tuple(row.keys()) == prior.SAVED_FIELDS
        assert type(row['elapsedMillis']) is int and 0 <= row['elapsedMillis'] <= 60000
    return rows


def extract(text, prefix):
    assert type(text) is str and type(prefix) is str
    return [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]


def check_sources(unit_text):
    """Bind all14 old, trial16 and control17 sources; return the per-seed identity map."""
    protocol = _protocol()
    prior = _prior_reader()
    old = _accepted_rows()
    prefixes = protocol['sourcePrefixes']
    old_rows = extract(unit_text, prefixes['old'])
    trial16_rows = extract(unit_text, prefixes['trial16'])
    control17_rows = extract(unit_text, prefixes['control17'])
    assert len(old_rows) == 14 and len(trial16_rows) == 14 and len(control17_rows) == 14
    seeds = [row['seed'] for row in old]
    assert [row.get('seed') for row in old_rows] == seeds, 'Old source case order changed'
    assert [row.get('seed') for row in trial16_rows] == seeds, 'Trial16 source case order changed'
    assert [row.get('seed') for row in control17_rows] == seeds, 'Control17 source case order changed'

    old_fields = (
        'seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
        'sourceIntentSha256', 'layoutSeed', 'routingSeed')
    for row, baseline in zip(old_rows, old):
        assert type(row) is dict and tuple(row.keys()) == old_fields, 'Old source schema/order changed'
        assert row == {key: baseline[key] for key in old_fields}, 'Old source binding changed: ' + baseline['seed']
        prior.long_text(row['seed'])
        prior.long_text(row['layoutSeed'])
        prior.long_text(row['routingSeed'])

    # The pinned policy reader independently validates every existing16 source derivation.
    trial16 = prior.inputs(trial16_rows, old)
    fields17 = tuple(protocol['controlSourceFields'])
    bindings = {}
    for row, baseline, row16 in zip(control17_rows, old, trial16_rows):
        assert type(row) is dict and tuple(row.keys()) == fields17, 'Control17 source schema/order changed'
        assert row['seed'] == baseline['seed'] == row16['seed']
        canonical = row16['sourceCanonical15']
        assert type(canonical) is str and row.get('sourceCanonical15') == canonical
        marker = 'placement-interpretation=15;'
        assert canonical.count(marker) == 1, 'Old canonical interpretation marker must be unique'
        derived17 = prior.digest(canonical.replace(marker, 'placement-interpretation=17;'))
        assert row['sourcePlanSha25617'] == derived17, 'Control17 canonical source digest changed'
        assert row['graph17'] == baseline['liveBoardGraphSha256'] == row16['graph16']
        assert row['intent17'] == baseline['sourceIntentSha256'] == row16['intent16']
        prior.long_text(row['layoutSeed17'])
        prior.long_text(row['routingSeed17'])
        assert row['layoutSeed17'] == baseline['layoutSeed'] == row16['layoutSeed16']
        assert row['routingSeed17'] == baseline['routingSeed'] == row16['routingSeed16']
        assert type(row['eligibleIds']) is list and row['eligibleIds'] == trial16[baseline['seed']]['eligibleIds']
        assert type(row['densityVersion17']) is int and row['densityVersion17'] == protocol['density']
        assert type(row['passivePosePolicy17']) is int and row['passivePosePolicy17'] == protocol['policyByVersion']['17']
        assert row['admissionIdentity17'] == protocol['admissionByVersion']['17']
        bindings[baseline['seed']] = {
            'seed': baseline['seed'],
            'sourcePlanSha25616': trial16[baseline['seed']]['sourcePlanSha25616'],
            'sourcePlanSha25617': derived17,
            'graph17': row['graph17'],
            'intent17': row['intent17'],
            'layoutSeed17': row['layoutSeed17'],
            'routingSeed17': row['routingSeed17'],
            'eligibleIds': list(row['eligibleIds']),
            'parts': baseline['parts'],
            'pads': baseline['pads'],
        }
    assert set(bindings) == set(seeds)
    return bindings


def check_raw(unit_text, bindings):
    """Return all15 raw records after exact sealed15/16 and control17 equality checks."""
    protocol = _protocol()
    prior = _prior_reader()
    rows = extract(unit_text, protocol['rawPrefix'])
    expected_rows = _read_json('raw-baselines.json').get('rows')
    assert type(expected_rows) is list and len(expected_rows) == 15
    assert type(rows) is list and len(rows) == 15, 'All15 untouched raw candidate branches required'
    expected_cases = [tuple(case) for case in protocol['rawCases']]
    assert [(row.get('seed'), row.get('attempt')) for row in rows] == expected_cases
    assert [(row.get('seed'), row.get('attempt')) for row in expected_rows] == expected_cases
    assert type(bindings) is dict and set(bindings) == set(prior.ROOTS)
    fields = tuple(protocol['rawFields'])
    for row, baseline in zip(rows, expected_rows):
        assert type(row) is dict and tuple(row.keys()) == fields, 'Raw candidate schema/order changed'
        assert type(row['seed']) is str and prior.long_text(row['seed']) == row['seed']
        assert type(row['attempt']) is int and row['attempt'] == baseline['attempt']
        assert row['seed'] == baseline['seed'] and row['seed'] in bindings
        for metric in ('geometry', 'evaluations', 'demand', 'courtyard'):
            old15, old16 = baseline[metric + '15'], baseline[metric + '16']
            current15, current17 = row[metric + '15'], row[metric + '17']
            if metric == 'geometry':
                assert type(current15) is str and re.fullmatch(r'[0-9a-f]{64}', current15)
                assert type(current17) is str and re.fullmatch(r'[0-9a-f]{64}', current17)
            else:
                assert type(current15) is int and type(current17) is int
            assert old15 == old16, 'Sealed untouched raw baseline is inconsistent'
            assert current15 == old15 and current17 == old16, 'Untouched raw candidate changed: ' + metric
            assert current15 == current17, 'Control17 changed untouched raw candidate: ' + metric
        assert 0 < row['evaluations15'] <= 60000 and row['demand15'] > 0 and row['courtyard15'] > 0
    return rows


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


def _phase_manifest(phase):
    assert phase in ('unit', 'routes')
    name = 'unit-suites.json' if phase == 'unit' else 'route-suites.json'
    doc = _read_json(name)
    assert type(doc) is list
    return doc


def _report_events(lines, protocol):
    prefixes = {
        protocol['sourcePrefixes']['old']: 'old',
        protocol['sourcePrefixes']['trial16']: 'trial16',
        protocol['sourcePrefixes']['control17']: 'control17',
        protocol['rawPrefix']: 'raw',
        protocol['posePrefix']: 'pose',
        protocol['routePrefix']: 'route',
    }
    events = []
    for line in lines:
        for prefix, name in prefixes.items():
            if line.startswith(prefix):
                events.append((name, line))
                break
    return events


def _suite_pass_lines(lines, pairs):
    all_suite_lines = [line for line in lines if line.startswith(SUITE_PASS_PREFIX)]
    assert len(all_suite_lines) == len(pairs), 'Suite PASS marker missing or duplicated'
    ordered_lines = []
    for _name, marker in pairs:
        prefix = 'PASS: ' + marker
        matches = [line for line in lines if line.startswith(prefix)]
        assert len(matches) == 1, 'Expected one suite PASS prefix: ' + marker.strip()
        actual = matches[0]
        suffix = actual[len(prefix):]
        assert SUITE_DETAIL.fullmatch(suffix), 'Suite PASS detail suffix is malformed: ' + marker.strip()
        ordered_lines.append(actual)
    assert all_suite_lines == ordered_lines, 'Suite PASS markers reordered or unrelated marker added'
    return ordered_lines


def check_phase(text, receipt, timing, suites):
    """Validate native timing, suite markers and details, terminal cleanup, and report order."""
    protocol = _protocol()
    assert type(text) is str and type(receipt) is str and type(timing) is dict
    assert set(timing) == TIMING_FIELDS, 'Native timing must contain exactly eight keys'
    phase = timing.get('phase')
    assert phase in ('unit', 'routes')
    pairs = _suite_pairs(suites)
    assert pairs == _suite_pairs(_phase_manifest(phase)), 'Caller suite order differs from frozen manifest'
    expected_count = 8 if phase == 'unit' else 2
    assert len(pairs) == expected_count, 'Native phase suite count changed'
    assert timing['status'] == 'PASS' and type(timing['exitCode']) is int and timing['exitCode'] == 0
    assert type(timing['suiteCount']) is int and timing['suiteCount'] == expected_count
    elapsed = timing['elapsedSeconds']
    assert type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0.0
    assert timing['scope'] == protocol['timingScope']
    assert timing['terminalCleanupIncluded'] is True
    assert timing['cleanupDuration'] == 'NOT_SEPARATELY_MEASURED'

    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    assert not any(FAILURE_LINE.search(line) for line in text_lines + receipt_lines), 'Native phase contains failure/timeout output'
    terminal = TERMINAL.format(count=expected_count)
    assert text_lines.count(terminal) == 1, 'Expected one exact native JVM suite terminal'
    assert text_lines.count(CLEANUP) == 1, 'Maintained terminal cleanup marker missing or duplicated'
    assert terminal not in receipt_lines and CLEANUP not in receipt_lines, 'Child receipt contains outer terminal data'
    terminal_at, cleanup_at = text_lines.index(terminal), text_lines.index(CLEANUP)
    assert terminal_at < cleanup_at

    receipt_passes = _suite_pass_lines(receipt_lines, pairs)
    text_passes = _suite_pass_lines(text_lines, pairs)
    assert receipt_passes == text_passes, 'Full suite PASS lines differ between terminal and child receipt'
    assert all(text_lines.index(line) < terminal_at for line in text_passes)

    if phase == 'unit':
        expected_labels = ['old'] * 14 + ['trial16'] * 14 + ['control17'] * 14 + ['raw'] * 15
        assert protocol['unitReportOrder'] == 'old14,trial16source14,control17source14,raw15; pure policy/admission emit only PASS markers'
    else:
        expected_labels = [label for _case in protocol['cases'] for label in ('pose', 'route')]
        assert protocol['routeReportOrder'] == 'pose,route per case;42 at15/16/17 then3 at15/16/17'
    receipt_events = _report_events(receipt_lines, protocol)
    text_events = _report_events(text_lines, protocol)
    assert [name for name, _ in receipt_events] == expected_labels, 'Child report rows missing, extra, or out of order'
    assert [name for name, _ in text_events] == expected_labels, 'Terminal report rows missing, extra, or out of order'
    assert receipt_events == text_events, 'Terminal and child report rows differ'
    assert all(text_lines.index(line) < terminal_at for _, line in text_events)

    allowed_prefixes = set(protocol['sourcePrefixes'].values()) | {
        protocol['rawPrefix'], protocol['posePrefix'], protocol['routePrefix']}
    for line in receipt_lines + text_lines:
        if line.startswith('PCB_'):
            assert any(line.startswith(prefix) for prefix in allowed_prefixes), 'Unknown PCB report appeared in phase'
    return {
        'status': 'PASS', 'phase': phase, 'suiteCount': expected_count,
        'suiteClasses': [name for name, _ in pairs],
        'orderedMarkers': [marker for _, marker in pairs],
        'suitePassLines': text_passes,
        'reportOrder': expected_labels,
    }


def _validate_control17_route(row, baseline15, binding, protocol):
    prior = _prior_reader()
    assert type(row) is dict and tuple(row.keys()) == tuple(prior.SAVED_FIELDS), 'Control17 route schema/order changed'
    assert type(baseline15) is dict and tuple(baseline15.keys()) == tuple(prior.SAVED_FIELDS)
    for key in baseline15:
        assert type(row[key]) is type(baseline15[key]), 'Control17 route field type changed: ' + key
    assert row['status'] == 'PASS' and type(row['version']) is int and row['version'] == 17
    assert type(row['seed']) is str and prior.long_text(row['seed']) == binding['seed']
    assert row['density'] == protocol['density']
    assert row['parts'] == binding['parts'] and row['pads'] == binding['pads']
    assert row['sourcePlanSha256'] == binding['sourcePlanSha25617']
    assert row['liveBoardGraphSha256'] == binding['graph17']
    assert row['sourceIntentSha256'] == binding['intent17']
    assert type(row['layoutSeed']) is str and prior.long_text(row['layoutSeed']) == binding['layoutSeed17']
    assert type(row['routingSeed']) is str and prior.long_text(row['routingSeed']) == binding['routingSeed17']
    assert row['excludedWorkbenchChrome'] == baseline15['excludedWorkbenchChrome']
    assert type(row['elapsedMillis']) is int and 0 < row['elapsedMillis'] <= 60000
    assert re.fullmatch(r'[0-9a-f]{64}', row['geometrySha256'])
    assert type(row['width']) is int and row['width'] > 0
    assert type(row['height']) is int and row['height'] > 0
    assert type(row['area']) is int and row['area'] == row['width'] * row['height']
    assert row['actualMargin'] >= row['requiredMargin'] >= 0
    assert row['requestedMargin'] == row['legacyRequestedMargin']
    assert type(row['acceptedPlanEvaluations']) is int and 0 < row['acceptedPlanEvaluations'] <= 6 * 60000
    assert type(row['placementRejections']) is int and 0 <= row['placementRejections'] <= 6
    assert type(row['oneFaceAttempts']) is int and 0 <= row['oneFaceAttempts'] <= 3
    assert type(row['twoLayerAttempts']) is int and 1 <= row['twoLayerAttempts'] <= 3
    assert type(row['routeAttempts']) is int
    assert row['routeAttempts'] == row['oneFaceAttempts'] + row['twoLayerAttempts'] <= 6
    ranked = row['rankedPlacementAttempts']
    assert type(ranked) is list and 1 <= len(ranked) <= 6 and len(set(ranked)) == len(ranked)
    assert all(type(attempt) is int and 0 <= attempt < 6 for attempt in ranked)
    assert row['selectedAttempt'] in ranked
    assert type(row['selectedQualityMilli']) is int and row['selectedQualityMilli'] >= 0
    assert type(row['routingOrders']) is int and row['routingOrders'] >= 0
    assert type(row['routingExpansions']) is int and row['routingExpansions'] >= 0
    stats = row['statisticsCanonical']
    assert type(stats) is str and ';outcome=SUCCESS;' in stats
    assert ';selected=P07_FULLER_TWO_LAYER;' in stats and ';placements=6;' in stats
    assert all(type(row[key]) is int and row[key] >= 0 for key in METRICS)


def _validate_control17_pose(row, binding, protocol):
    assert type(row) is dict and tuple(row.keys()) == tuple(protocol['poseFields']), 'Control17 pose schema/order changed'
    assert row['seed'] == binding['seed']
    assert type(row['version']) is int and row['version'] == 17
    assert row['issuedAdmissionIdentity'] == protocol['admissionByVersion']['17']
    for key in ('top0', 'top90'):
        assert type(row[key]) is list and all(type(value) is str for value in row[key])
        assert row[key] == sorted(set(row[key])), 'Pose census must be ordered and unique'
    assert not set(row['top0']) & set(row['top90'])
    assert row['top0'] == binding['eligibleIds'] and row['top90'] == [], 'Control17 passives must remain TOP0-only'


def assess_routes(route_rows, pose_rows, bindings):
    """Reproduce four frozen routes and describe two control17 metric deltas."""
    protocol = _protocol()
    prior = _prior_reader()
    route_doc = _read_json('route-baselines.json')
    pose_doc = _read_json('pose-baselines.json')
    old_routes, old_poses = route_doc.get('rows'), pose_doc.get('rows')
    assert type(old_routes) is list and len(old_routes) == 4
    assert type(old_poses) is list and len(old_poses) == 4
    old_cases = [('42', 15), ('42', 16), ('3', 15), ('3', 16)]
    assert [(row.get('seed'), row.get('version')) for row in old_routes] == old_cases
    assert [(row.get('seed'), row.get('version')) for row in old_poses] == old_cases
    assert all(type(row) is dict and tuple(row.keys()) == tuple(prior.SAVED_FIELDS) for row in old_routes)
    assert all(type(row) is dict and tuple(row.keys()) == tuple(protocol['poseFields']) for row in old_poses)
    assert type(bindings) is dict and set(bindings) == set(prior.ROOTS)
    assert type(route_rows) is list and len(route_rows) == 6, 'All six ordered route cases required'
    assert type(pose_rows) is list and len(pose_rows) == 6, 'All six ordered pose cases required'
    case_order = [tuple(case) for case in protocol['cases']]
    assert [(row.get('seed'), row.get('version')) for row in route_rows] == case_order, 'Route case order changed'
    assert [(row.get('seed'), row.get('version')) for row in pose_rows] == case_order, 'Pose case order changed'

    old_route_by_case = {(row['seed'], row['version']): row for row in old_routes}
    old_pose_by_case = {(row['seed'], row['version']): row for row in old_poses}
    assert len(old_route_by_case) == 4 and len(old_pose_by_case) == 4
    for index, (seed, version) in enumerate(case_order):
        row, pose, binding = route_rows[index], pose_rows[index], bindings[seed]
        if version in (15, 16):
            base = old_route_by_case[(seed, version)]
            pose_base = old_pose_by_case[(seed, version)]
            assert type(row) is dict and tuple(row.keys()) == tuple(prior.SAVED_FIELDS)
            assert type(row['elapsedMillis']) is int and 0 < row['elapsedMillis'] <= 60000
            assert all(row[key] == value for key, value in base.items() if key != 'elapsedMillis'), 'Published old route changed: ' + seed + '/' + str(version)
            legacy_binding = {key: binding[key] for key in ('seed', 'sourcePlanSha25616', 'eligibleIds')}
            prior.route_case(row, base, version, legacy_binding)
            assert type(pose) is dict and tuple(pose.keys()) == tuple(protocol['poseFields'])
            assert pose == pose_base, 'Published old pose census changed: ' + seed + '/' + str(version)
            prior.pose_case(pose, base, version, legacy_binding)
        else:
            _validate_control17_route(row, old_route_by_case[(seed, 15)], binding, protocol)
            _validate_control17_pose(pose, binding, protocol)

    deltas = []
    for seed in ('42', '3'):
        by_version = {version: route_rows[case_order.index((seed, version))] for version in (15, 16, 17)}
        deltas.append({
            'seed': seed,
            'vs15': {key: by_version[17][key] - by_version[15][key] for key in METRICS},
            'vs16': {key: by_version[17][key] - by_version[16][key] for key in METRICS},
        })
    return {
        'status': 'PASS',
        'old4Reproductions': [
            {'seed': seed, 'version': version, 'result': 'EXACT'} for seed, version in old_cases],
        'control2DescriptiveDeltas': deltas,
        'metricInterpretation': 'Descriptive only; no non-regression or fitness acceptance claim.',
    }
