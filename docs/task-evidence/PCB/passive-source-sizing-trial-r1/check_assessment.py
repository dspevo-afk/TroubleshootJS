"""Pre-runtime synthetic canaries for assess.py; reads only sealed fixture inputs."""
import copy
import hashlib
import json
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
PRIOR = HERE.parent / 'passive-pose-policy-r1'
PROPOSAL_SHA = '660278926345ca2a707ac8bce84f6ad1ba16fcbcbe909b55bc4dd4b5c92a98de'
PROTOCOL_SHA = '48b16c668aba3a5606748fd3985175941740b17ac034f3c365f816a6c926761e'
PRIOR_READER_SHA = 'c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26'
PRIOR_UNIT_SHA = assess.SOURCE_FIXTURE_SHA
RESULTS_PATH = HERE / 'assessment-canary-results.json'


def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()


def load_json(name, expected_sha=None):
    data = (HERE / name).read_bytes()
    if expected_sha is not None:
        require(sha_bytes(data) == expected_sha, name + ' changed from its frozen digest')
    return json.loads(data.decode('utf-8'))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def fixture_sources():
    path = PRIOR / 'unit.txt'
    data = path.read_bytes()
    require(sha_bytes(data) == PRIOR_UNIT_SHA, 'pinned prior source fixture digest changed')
    prefix = 'PCB_PASSIVE_POLICY_INPUT '
    rows = []
    for line in data.decode('utf-8-sig').splitlines():
        if line.startswith(prefix):
            rows.append(json.loads(line[len(prefix):]))
    require(len(rows) == 14 and [row.get('seed') for row in rows] == assess.ROOTS,
            'pinned trial16 sourceCanonical15 fixture count/order changed')
    require(all(tuple(row) == assess.SOURCE16_FIELDS for row in rows),
            'pinned trial16 source fixture schema changed')
    return rows


def independent_version_sha(canonical, version):
    marker = 'placement-interpretation=15;'
    require(canonical.count(marker) == 1, 'fixture interpretation marker not unique')
    return sha_bytes(canonical.replace(marker, 'placement-interpretation=%d;' % version, 1).encode('utf-8'))


def json_line(prefix, row):
    return prefix + json.dumps(row, separators=(',', ':'), ensure_ascii=False)


def make_unit(fixture, old_rows, raw_baselines):
    source18 = []
    for old, control in zip(old_rows, fixture):
        source18.append({
            'seed': old['seed'], 'sourceCanonical15': control['sourceCanonical15'],
            'sourcePlanSha25618': independent_version_sha(control['sourceCanonical15'], 18),
            'graph18': control['graph16'], 'intent18': control['intent16'],
            'layoutSeed18': control['layoutSeed16'], 'routingSeed18': control['routingSeed16'],
            'eligibleIds': list(control['eligibleIds']), 'densityVersion18': 14,
            'passivePosePolicy18': 3, 'admissionIdentity18': 'RB56_BOARD_NORMAL@4'})
    raw18 = []
    for old in raw_baselines:
        raw18.append({
            'seed': old['seed'], 'attempt': old['attempt'],
            'geometry15': old['geometry15'], 'geometry18': old['geometry15'],
            'evaluations15': old['evaluations15'], 'evaluations18': old['evaluations15'],
            'demand15': old['demand15'], 'demand18': old['demand15'],
            'courtyard15': old['courtyard15'], 'courtyard18': old['courtyard15']})
    source15 = [{'seed': old['seed'], 'parts': old['parts'], 'pads': old['pads'],
                 'sourcePlanSha256': old['sourcePlanSha256'],
                 'liveBoardGraphSha256': old['liveBoardGraphSha256'],
                 'sourceIntentSha256': old['sourceIntentSha256'],
                 'layoutSeed': old['layoutSeed'], 'routingSeed': old['routingSeed']}
                for old in old_rows]
    records = ([json_line(assess.SOURCE18_PREFIX, row) for row in source18] +
               [json_line(assess.RAW_PREFIX, row) for row in raw18] +
               [json_line(assess.SOURCE15_PREFIX, row) for row in source15] +
               [json_line(assess.INPUT16_PREFIX, row) for row in fixture])
    return '\n'.join(records) + '\n', source18, raw18


def make_routes(old_rows, bindings):
    routes, poses, whitespace = [], [], []
    for index, (seed, binding) in enumerate(bindings.items()):
        old = old_rows[index]
        old_route = copy.deepcopy(old)
        routes.append(old_route)
        new = copy.deepcopy(old)
        new['version'] = 18
        new['sourcePlanSha256'] = binding['sourcePlanSha25618']
        new['geometrySha256'] = ('a' if index % 2 == 0 else 'b') * 64
        if index == 0:
            new['width'] -= 1
            new['area'] = new['width'] * new['height']
        if seed == '42':
            # Exercise native coordinates against an off-grid outline origin;
            # the saved15 row above remains byte-for-byte pinned.
            new['outlineX'] += 3
            new['outlineY'] += 7
        routes.append(new)
        ids = list(binding['eligibleIds'])
        poses.append({'seed': seed, 'issuedAdmissionIdentity': 'RB56_BOARD_NORMAL@1',
                      'version': 15, 'top0': ids, 'top90': []})
        selected90 = [ids[-1]] if index == 0 else []
        poses.append({'seed': seed, 'issuedAdmissionIdentity': 'RB56_BOARD_NORMAL@4',
                      'version': 18, 'top0': [item for item in ids if item not in selected90],
                      'top90': selected90})
        for version, route in ((15, old_route), (18, new)):
            area = route['largestProtectedEmptyRectangleArea']
            width = 10
            height = area // width
            require(area > 0 and area % width == 0 and height % 10 == 0,
                    'synthetic baseline rectangle cannot meet grid10')
            whitespace.append({
                'seed': seed, 'version': version, 'parts': route['parts'], 'gridUnits': 10,
                'sampledCells': 1000, 'packageAccessOccupiedCells': 260,
                'protectedFreeCells': 740, 'protectedFreeFraction': 0.74,
                'largestProtectedEmptyRectangle': {'x': route['outlineX'] + 30,
                                                    'y': route['outlineY'] + 20, 'width': width,
                                                    'height': height, 'area': area},
                'scope': assess.WHITESPACE_SCOPE})
    return routes, poses, whitespace


def pass_detail(marker):
    if 'policy contracts' in marker:
        return 'assertions=91 geometries=7 sequence=63/63 visits=126'
    if 'admission contracts' in marker:
        return 'assertions=31'
    if 'raw' in marker:
        return 'rows=5 routes=0 assertions=15'
    if 'source contracts' in marker:
        return 'roots=14 assertions=43'
    if 'route' in marker:
        return 'roots=1 rows=2 assertions=76 geometries=7 sequence=63/63 visits=126'
    return ''


def phase_bundle(phase, report_lines):
    suite_file = 'unit-suites.json' if phase == 'unit' else 'routes-suites.json'
    suites = load_json(suite_file)
    pairs = assess._suite_pairs(suites)
    passes = ['PASS: ' + marker + pass_detail(marker) for _, marker in pairs]
    terminal = ('PASS: focused current contracts; %d Java suites. Full matrix and independent oracles NOT RUN.'
                % len(pairs))
    cleanup = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
    receipt = '\n'.join(passes + report_lines) + '\n'
    text = '\n'.join(passes + report_lines + [terminal, cleanup]) + '\n'
    timing = {'status': 'PASS', 'exitCode': 0, 'elapsedSeconds': 12.5 if phase == 'unit' else 38.0,
              'suiteCount': len(pairs), 'phase': phase, 'scope': assess.TIMING_SCOPE,
              'terminalCleanupIncluded': True, 'cleanupDuration': 'NOT_SEPARATELY_MEASURED'}
    return text, receipt, timing, suites


def unit_report_lines(unit_text):
    lines = []
    for line in unit_text.splitlines():
        if line.startswith(('PCB_SOURCE_SIZED_POSE_', 'PCB_PASSIVE_POSE_SOURCE_CASE ',
                            'PCB_PASSIVE_POLICY_INPUT ')):
            lines.append(line)
    return lines


def route_report_lines(routes, poses, whitespace):
    lines = []
    for index in range(28):
        lines.extend((json_line(assess.WHITESPACE_PREFIX, whitespace[index]),
                      json_line(assess.POSE_PREFIX, poses[index]),
                      json_line(assess.ROUTE_PREFIX, routes[index])))
    return lines


def alter_rows(text, prefix, index, change):
    lines = text.splitlines()
    matches = [position for position, line in enumerate(lines) if line.startswith(prefix)]
    require(index < len(matches), 'test mutation row not found')
    position = matches[index]
    row = json.loads(lines[position][len(prefix):])
    change(row)
    lines[position] = json_line(prefix, row)
    return '\n'.join(lines) + '\n'


def alter_report_bundle(bundle, edit):
    text, receipt, timing, suites = bundle
    return edit(text), edit(receipt), timing, suites


def expect_reject(name, fn, results):
    try:
        fn()
    except (AssertionError, KeyError, TypeError, ValueError, OverflowError):
        results.append({'id': name, 'result': 'PASS', 'expected': 'REJECT', 'observed': 'REJECT'})
        return
    raise AssertionError('negative canary unexpectedly accepted: ' + name)


def expect_strict_fail(name, fn, results):
    observed = fn()
    require(observed['status'] == 'FAIL' and observed['strictFitness']['status'] == 'FAIL',
            'complete strict failure was incorrectly accepted: ' + name)
    results.append({'id': name, 'result': 'PASS', 'expected': 'FAIL', 'observed': observed['status']})


def insert_before_terminal(text, line):
    lines = text.splitlines()
    terminal_at = next(i for i, item in enumerate(lines) if item.startswith('PASS: focused current contracts;'))
    lines.insert(terminal_at, line)
    return '\n'.join(lines) + '\n'


def remove_one(text, prefix, index=0):
    lines = text.splitlines()
    matches = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    del lines[matches[index]]
    return '\n'.join(lines) + '\n'


def replace_report_order(text, first_prefix, first_index, second_prefix, second_index):
    lines = text.splitlines()
    first = [i for i, line in enumerate(lines) if line.startswith(first_prefix)][first_index]
    second = [i for i, line in enumerate(lines) if line.startswith(second_prefix)][second_index]
    lines[first], lines[second] = lines[second], lines[first]
    return '\n'.join(lines) + '\n'


def main():
    manifest = load_json('assessment-canaries.json')
    proposal = load_json('proposal.json', PROPOSAL_SHA)
    protocol = load_json('protocol.json', PROTOCOL_SHA)
    require(proposal['baseCommit'] == 'e464dbfd0bffc052e7e233ec59e012e86c16f8c2',
            'proposal base commit changed')
    require(proposal['preFreezeSafetyAmendment'] and protocol['roots'] == assess.ROOTS,
            'proposal amendment or protocol roots missing')
    require(len(protocol['cases']) == 28 and protocol['policyByVersion']['18'] == 3 and
            protocol['density'] == 14 and protocol['admissionByVersion']['18'] == 'RB56_BOARD_NORMAL@4',
            'trial18 protocol identities changed')
    require(protocol['whitespaceScope'] == assess.WHITESPACE_SCOPE and
            protocol['fitnessMetrics'] == list(assess.METRICS), 'protocol reader contracts changed')
    unit_suites = load_json('unit-suites.json', assess.UNIT_SUITES_SHA)
    route_suites = load_json('routes-suites.json', assess.ROUTES_SUITES_SHA)
    require(len(unit_suites) == 8 and len(route_suites) == 14, 'frozen native suite counts changed')
    fixture = fixture_sources()
    old_rows = assess._baselines()
    raw_baselines = assess._raw_baselines()
    unit_text, _, _ = make_unit(fixture, old_rows, raw_baselines)
    bindings = assess.check_sources(unit_text)
    raw_rows = assess.check_raw(unit_text, bindings)
    routes, poses, whitespace = make_routes(old_rows, bindings)
    offgrid_index = assess.ROOTS.index('42') * 2 + 1
    offgrid_rect = whitespace[offgrid_index]['largestProtectedEmptyRectangle']
    require(routes[offgrid_index]['outlineX'] % 10 != 0 and routes[offgrid_index]['outlineY'] % 10 != 0 and
            offgrid_rect['x'] % 10 != 0 and offgrid_rect['y'] % 10 != 0 and
            (offgrid_rect['x'] - routes[offgrid_index]['outlineX']) % 10 == 0 and
            (offgrid_rect['y'] - routes[offgrid_index]['outlineY']) % 10 == 0,
            'synthetic positive rectangle must align relative to an off-grid outline origin')
    route_result = assess.assess_routes(routes, poses, whitespace, bindings)
    require(route_result['status'] == 'PASS' and route_result['strictFitness']['status'] == 'PASS',
            'complete positive synthetic trial did not pass')
    require(len(bindings) == 14 and len(raw_rows) == 15 and len(routes) == 28 and
            len(poses) == 28 and len(whitespace) == 28, 'synthetic fixture coverage changed')

    results = []
    unit_bundle = phase_bundle('unit', unit_report_lines(unit_text))
    phase_unit = assess.check_phase(*unit_bundle)
    require(phase_unit['status'] == 'PASS' and phase_unit['suiteCount'] == 8 and
            phase_unit['reportRows'] == 57, 'positive unit phase canary failed')
    results.append({'id': 'unit-phase-ordered-source18-raw15-source15-source16',
                    'result': 'PASS', 'expected': 'PASS', 'observed': 'PASS'})
    route_bundle = phase_bundle('routes', route_report_lines(routes, poses, whitespace))
    phase_routes = assess.check_phase(*route_bundle)
    require(phase_routes['status'] == 'PASS' and phase_routes['suiteCount'] == 14 and
            phase_routes['reportRows'] == 84, 'positive route phase canary failed')
    results.append({'id': 'routes-phase-all28-whitespace-pose-route-cases',
                    'result': 'PASS', 'expected': 'PASS', 'observed': 'PASS'})
    results.append({'id': 'complete-strict-improvement-with-actual-canonical-top90',
                    'result': 'PASS', 'expected': 'PASS', 'observed': 'PASS'})
    results.append({'id': 'whitespace-relative-grid-offgrid-origin', 'result': 'PASS',
                    'expected': 'PASS', 'observed': 'PASS'})

    # Phase-level structural and lifecycle negatives.
    def phase_reject(name, bundle):
        expect_reject(name, lambda: assess.check_phase(*bundle), results)
    text, receipt, timing, suites = route_bundle
    phase_reject('phase-duplicate-report', (insert_before_terminal(text, next(
        line for line in text.splitlines() if line.startswith(assess.ROUTE_PREFIX))),
        receipt + next(line for line in receipt.splitlines() if line.startswith(assess.ROUTE_PREFIX)) + '\n',
        timing, suites))
    phase_reject('phase-reordered-report', alter_report_bundle(
        route_bundle, lambda value: replace_report_order(value, assess.WHITESPACE_PREFIX, 0,
                                                          assess.WHITESPACE_PREFIX, 1)))
    phase_reject('phase-missing-report', alter_report_bundle(
        route_bundle, lambda value: remove_one(value, assess.POSE_PREFIX, 0)))
    phase_reject('phase-unknown-report', (insert_before_terminal(text, 'PCB_UNDECLARED_REPORT {}'),
        receipt + 'PCB_UNDECLARED_REPORT {}\n', timing, suites))
    phase_reject('phase-failure-line', (insert_before_terminal(text, 'FAIL: canary failure'),
        receipt + 'FAIL: canary failure\n', timing, suites))
    phase_reject('phase-timeout', (insert_before_terminal(text, 'TIMEOUT: bounded native phase'),
        receipt + 'TIMEOUT: bounded native phase\n', timing, suites))
    phase_reject('phase-cleanup-missing', (text.replace(
        'CLEANUP: current JVM contract classes and task-owned scratch removed.\n', ''),
        receipt, timing, suites))
    phase_reject('phase-terminal-line-changed', (text.replace(
        'PASS: focused current contracts; 14 Java suites.',
        'PASS: focused current contracts; 13 Java suites.'), receipt, timing, suites))
    altered_receipt = receipt.replace('PASS: ' + route_suites[0][1],
                                      'PASS: ' + route_suites[0][1] + ' rows=3', 1)
    phase_reject('phase-log-receipt-pass-mismatch', (text, altered_receipt, timing, suites))
    marker_variants = []
    unit_text_phase, unit_receipt_phase, unit_timing, unit_manifest = unit_bundle
    source_pass = 'PASS: ' + unit_suites[0][1]
    source_text_bad = unit_text_phase.replace(source_pass + 'roots=14', source_pass + 'roots=15', 1)
    source_receipt_bad = unit_receipt_phase.replace(source_pass + 'roots=14', source_pass + 'roots=15', 1)
    expect_reject('marker-source-root-count',
                  lambda: assess.check_phase(source_text_bad, source_receipt_bad,
                                             unit_timing, unit_manifest), marker_variants)
    policy_pass = 'PASS: ' + unit_suites[5][1]
    policy_text_bad = unit_text_phase.replace(policy_pass + 'assertions=91 geometries=7 sequence=63/63',
                                               policy_pass + 'assertions=91 geometries=7 sequence=62/63', 1)
    policy_receipt_bad = unit_receipt_phase.replace(policy_pass + 'assertions=91 geometries=7 sequence=63/63',
                                                    policy_pass + 'assertions=91 geometries=7 sequence=62/63', 1)
    expect_reject('marker-policy-coverage-count',
                  lambda: assess.check_phase(policy_text_bad, policy_receipt_bad,
                                             unit_timing, unit_manifest), marker_variants)
    require(len(marker_variants) == 2, 'suite marker count canaries did not execute')
    results.append({'id': 'phase-marker-count-mismatch', 'result': 'PASS',
                    'expected': 'REJECT', 'observed': 'REJECT'})

    # Source identity and explicit epoch/admission guards.
    for name, key, value in (
        ('source-graph-mismatch', 'graph18', '0' * 64),
        ('source-digest-mismatch', 'sourcePlanSha25618', '0' * 64),
        ('source-signed-long-overflow', 'layoutSeed18', '9223372036854775808')):
        changed = alter_rows(unit_text, assess.SOURCE18_PREFIX, 0,
                             lambda row, k=key, v=value: row.__setitem__(k, v))
        expect_reject(name, lambda changed=changed: assess.check_sources(changed), results)
    changed = alter_rows(unit_text, assess.SOURCE18_PREFIX, 0,
                         lambda row: row['eligibleIds'].pop())
    expect_reject('source-population-mismatch', lambda: assess.check_sources(changed), results)
    for name, key, value in (
        ('source-density-mismatch', 'densityVersion18', 13),
        ('source-policy-mismatch', 'passivePosePolicy18', 2),
        ('source-admission-mismatch', 'admissionIdentity18', 'RB56_BOARD_NORMAL@3')):
        changed = alter_rows(unit_text, assess.SOURCE18_PREFIX, 0,
                             lambda row, k=key, v=value: row.__setitem__(k, v))
        expect_reject(name, lambda changed=changed: assess.check_sources(changed), results)

    for name, mutation in (
        ('source15-graph-mismatch', lambda row: row.__setitem__('liveBoardGraphSha256', '0' * 64)),
        ('source15-seed-mismatch', lambda row: row.__setitem__('seed', '1')),
        ('source15-hash-mismatch', lambda row: row.__setitem__('sourcePlanSha256', '0' * 64)),
        ('source15-parts-mismatch', lambda row: row.__setitem__('parts', row['parts'] + 1)),
        ('source15-pads-mismatch', lambda row: row.__setitem__('pads', row['pads'] + 1)),
        ('source15-addfield', lambda row: row.__setitem__('unexpected', True)),
        ('source15-layout-seed-overflow',
         lambda row: row.__setitem__('layoutSeed', '9223372036854775808')),
        ('source15-routing-seed-mismatch',
         lambda row: row.__setitem__('routingSeed', str(int(row['routingSeed']) + 1)))):
        changed = alter_rows(unit_text, assess.SOURCE15_PREFIX, 0, mutation)
        expect_reject(name, lambda changed=changed: assess.check_sources(changed), results)

    changed_raw = alter_rows(unit_text, assess.RAW_PREFIX, 0,
                             lambda row: row.__setitem__('geometry15', '0' * 64))
    expect_reject('raw-old15-mutation', lambda: assess.check_raw(changed_raw, bindings), results)
    for name, key, value in (
        ('raw18-geometry-drift', 'geometry18', '0' * 64),
        ('raw18-evaluations-drift', 'evaluations18', None),
        ('raw18-demand-drift', 'demand18', None),
        ('raw18-courtyard-drift', 'courtyard18', None)):
        if value is None:
            changed_raw = alter_rows(unit_text, assess.RAW_PREFIX, 0,
                                     lambda row, field=key: row.__setitem__(field, row[field] + 1))
        else:
            changed_raw = alter_rows(unit_text, assess.RAW_PREFIX, 0,
                                     lambda row, field=key, item=value: row.__setitem__(field, item))
        expect_reject(name, lambda changed_raw=changed_raw: assess.check_raw(changed_raw, bindings), results)

    # Full route assessment negatives include fidelity, required positive fitness, and whitespace.
    changed_routes = copy.deepcopy(routes)
    changed_routes[0]['area'] += 1
    expect_reject('route-old15-nontime-mutation',
                  lambda: assess.assess_routes(changed_routes, poses, whitespace, bindings), results)
    expect_reject('routes-missing-case',
                  lambda: assess.assess_routes(routes[:-1], poses, whitespace, bindings), results)

    regressed = copy.deepcopy(routes)
    regressed[1]['copperManhattanLength'] += 1
    expect_strict_fail('strict-metric-regression-returns-fail',
                       lambda: assess.assess_routes(regressed, poses, whitespace, bindings), results)
    unchanged = copy.deepcopy(routes)
    unchanged[1]['width'] = old_rows[0]['width']
    unchanged[1]['area'] = old_rows[0]['area']
    expect_strict_fail('strict-no-improvement-returns-fail',
                       lambda: assess.assess_routes(unchanged, poses, whitespace, bindings), results)
    no_turn = copy.deepcopy(poses)
    for index in range(1, len(no_turn), 2):
        no_turn[index]['top0'] = list(bindings[no_turn[index]['seed']]['eligibleIds'])
        no_turn[index]['top90'] = []
    expect_strict_fail('strict-no-top90-returns-fail',
                       lambda: assess.assess_routes(routes, no_turn, whitespace, bindings), results)

    expect_reject('whitespace-missing-case',
                  lambda: assess.assess_routes(routes, poses, whitespace[:-1], bindings), results)
    reordered_ws = copy.deepcopy(whitespace)
    reordered_ws[0], reordered_ws[1] = reordered_ws[1], reordered_ws[0]
    expect_reject('whitespace-reordered-case',
                  lambda: assess.assess_routes(routes, poses, reordered_ws, bindings), results)
    expect_reject('whitespace-extra-case',
                  lambda: assess.assess_routes(routes, poses, whitespace + [copy.deepcopy(whitespace[-1])], bindings), results)
    typed_ws = copy.deepcopy(whitespace)
    typed_ws[0]['gridUnits'] = True
    expect_reject('whitespace-typed-grid',
                  lambda: assess.assess_routes(routes, poses, typed_ws, bindings), results)
    fraction_ws = copy.deepcopy(whitespace)
    fraction_ws[0]['protectedFreeFraction'] = 0.75
    expect_reject('whitespace-free-fraction-mismatch',
                  lambda: assess.assess_routes(routes, poses, fraction_ws, bindings), results)
    area_ws = copy.deepcopy(whitespace)
    area_ws[0]['largestProtectedEmptyRectangle']['area'] += 10
    expect_reject('whitespace-area-mismatch',
                  lambda: assess.assess_routes(routes, poses, area_ws, bindings), results)
    relative_ws = copy.deepcopy(whitespace)
    relative_ws[offgrid_index]['largestProtectedEmptyRectangle']['x'] += 1
    expect_reject('whitespace-relative-grid-drift',
                  lambda: assess.assess_routes(routes, poses, relative_ws, bindings), results)
    typed_rect_ws = copy.deepcopy(whitespace)
    typed_rect_ws[0]['largestProtectedEmptyRectangle']['x'] = True
    expect_reject('whitespace-rectangle-coordinate-type',
                  lambda: assess.assess_routes(routes, poses, typed_rect_ws, bindings), results)
    legacy_rect_ws = copy.deepcopy(whitespace)
    rect = legacy_rect_ws[0]['largestProtectedEmptyRectangle']
    legacy_rect_ws[0]['largestProtectedEmptyRectangle'] = [rect['x'], rect['y'], rect['width'],
                                                           rect['height'], rect['area']]
    expect_reject('whitespace-legacy-list',
                  lambda: assess.assess_routes(routes, poses, legacy_rect_ws, bindings), results)
    population_ws = copy.deepcopy(whitespace)
    population_ws[0]['parts'] += 1
    expect_reject('whitespace-population-mismatch',
                  lambda: assess.assess_routes(routes, poses, population_ws, bindings), results)
    bad_status = copy.deepcopy(routes)
    bad_status[1]['status'] = 'FAIL'
    expect_reject('route-status-failure',
                  lambda: assess.assess_routes(bad_status, poses, whitespace, bindings), results)
    over_cap = copy.deepcopy(routes)
    over_cap[1]['acceptedPlanEvaluations'] = 360001
    expect_reject('route-cap-exceeded',
                  lambda: assess.assess_routes(over_cap, poses, whitespace, bindings), results)
    bad_pose = copy.deepcopy(poses)
    bad_pose[1]['issuedAdmissionIdentity'] = 'RB56_BOARD_NORMAL@3'
    expect_reject('pose-admission-mismatch',
                  lambda: assess.assess_routes(routes, bad_pose, whitespace, bindings), results)

    declared = manifest['positive'] + manifest['negative']
    observed = [row['id'] for row in results]
    require(observed == declared, 'canary manifest and executed scenario order differ')
    require(all(row['result'] == 'PASS' for row in results), 'one or more synthetic canaries failed')
    json.dumps(bindings, ensure_ascii=False)
    json.dumps(route_result, ensure_ascii=False)

    paths = {
        'assess.py': HERE / 'assess.py',
        'check_assessment.py': HERE / 'check_assessment.py',
        'assessment-canaries.json': HERE / 'assessment-canaries.json',
        'proposal.json': HERE / 'proposal.json',
        'protocol.json': HERE / 'protocol.json',
        'unit-suites.json': HERE / 'unit-suites.json',
        'routes-suites.json': HERE / 'routes-suites.json',
        'accepted15-baselines.json': HERE / 'accepted15-baselines.json',
        'raw-baselines.json': HERE / 'raw-baselines.json',
        'passive-pose-policy-r1/assess.py': PRIOR / 'assess.py',
        'passive-pose-policy-r1/unit.txt': PRIOR / 'unit.txt'}
    hashes = {name: sha_bytes(path.read_bytes()) for name, path in paths.items()}
    require(hashes['passive-pose-policy-r1/assess.py'] == PRIOR_READER_SHA and
            hashes['passive-pose-policy-r1/unit.txt'] == PRIOR_UNIT_SHA,
            'pinned prior reader/source fixture digest changed')
    summary = {
        'schema': 'pcb-source-sized-pose-reader-canary-results-v1',
        'status': 'PASS', 'phase': 'PRE_RUNTIME_SYNTHETIC_ONLY',
        'baseCommit': proposal['baseCommit'], 'canaryCount': len(results),
        'positiveCount': len(manifest['positive']), 'negativeCount': len(manifest['negative']),
        'unitPhase': {'suiteCount': 8, 'reportRows': 57, 'source18Roots': 14,
                      'rawRows': 15, 'source15Roots': 14, 'source16Roots': 14},
        'routePhase': {'suiteCount': 14, 'cases': 28, 'whitespaceRows': 28,
                       'poseRows': 28, 'routeRows': 28},
        'strictPositive': {'status': route_result['status'],
                           'metricImprovementRoots': len(route_result['strictFitness']['strictImprovements']),
                           'actualTop90Roots': len(route_result['strictFitness']['selectedTop90Cases'])},
        'canaries': results, 'consumedSha256': hashes,
        'nativeRuntimeEvidence': 'NOT RUN',
        'limits': ['Synthetic reader canaries do not establish native generation or routing behavior.',
                   'No runtime output, new source output, or control runtime output was read.']}
    RESULTS_PATH.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: %d pre-runtime synthetic reader canaries' % len(results))


if __name__ == '__main__':
    main()
