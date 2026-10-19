"""Synthetic-only canaries for passive-sizing-control-r1.assess."""
import copy
import hashlib
import json
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
PUBLISHED_UNIT = HERE.parent / 'passive-pose-policy-r1' / 'unit.txt'
SOURCE_PREFIX = 'PCB_PASSIVE_POLICY_INPUT '
CASES = [
    'complete-source-raw-route-chain',
    'unit-phase-contract-pass',
    'routes-phase-contract-pass',
    'source-case-order-rejected',
    'control17-density-policy-admission-rejected',
    'source-graph-and-signed-long-rejected',
    'source17-digest-and-canonical-rejected',
    'untouched-raw-candidate-change-rejected',
    'published-old-route-metric-change-rejected',
    'control17-pose-orientation-and-admission-rejected',
    'route-case-order-rejected',
    'phase-report-order-rejected',
    'phase-missing-suite-marker-rejected',
    'phase-failure-and-timeout-rejected',
    'phase-cleanup-missing-or-early-rejected',
    'phase-timing-schema-and-scope-rejected',
    'phase-duplicate-suite-marker-rejected',
    'phase-suite-marker-order-rejected',
    'phase-log-receipt-suffix-mismatch-rejected',
    'phase-unrelated-suffix-rejected',
]
SUITE_DETAILS = {
    'PcbPassivePosePolicyContractTest': 'assertions=245 geometries=7 sequence=63/63 visits=126',
    'PcbPassivePoseAdmissionContractTest': '77 assertions',
    'PcbPassivePoseSourceContractTest': 'roots=14 assertions=114',
    'PcbPassivePoseTrialSourceContractTest': 'roots=14 assertions=128',
    'PcbPassiveSizingSourceContractTest': 'roots=14 assertions=14',
    'PcbPassiveSizingRawContractTest': 'rows=5 routes=0',
    'PcbPassiveSizingRawContractTestGroup2': 'rows=5 routes=0',
    'PcbPassiveSizingRawContractTestGroup3': 'rows=5 routes=0',
    'PcbPassiveSizingRouteContractTest': 'roots=1 rows=3',
    'PcbPassiveSizingRouteContractTestGroup2': 'roots=1 rows=3',
}
ASSERTIONS = 0
RESULTS = []


def require(condition, message):
    global ASSERTIONS
    ASSERTIONS += 1
    if not condition:
        raise AssertionError(message)


def expect_rejection(action):
    global ASSERTIONS
    try:
        action()
    except (AssertionError, KeyError, TypeError, ValueError, FileNotFoundError):
        ASSERTIONS += 1
        return
    raise AssertionError('Mutated evidence was accepted')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def source_fixtures():
    data = PUBLISHED_UNIT.read_bytes()
    require(sha256(data) == assess.PUBLISHED_POLICY_UNIT_SHA256,
            'Published source fixture hash differs from its pin')
    text = data.decode('utf-8-sig')
    # Retain only the already-published trial16 source rows as sourceCanonical fixtures.
    return [json.loads(line[len(SOURCE_PREFIX):]) for line in text.splitlines()
            if line.startswith(SOURCE_PREFIX)]


def line(prefix, row):
    return prefix + json.dumps(row, separators=(',', ':'), ensure_ascii=False)


def build_unit_fixture():
    protocol = assess._protocol()
    old = assess._accepted_rows()
    trial16 = source_fixtures()
    require(len(trial16) == 14, 'Expected fourteen pinned sourceCanonical fixtures')
    old_fields = ('seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
                  'sourceIntentSha256', 'layoutSeed', 'routingSeed')
    old_sources = [{key: row[key] for key in old_fields} for row in old]
    control17 = []
    for row16 in trial16:
        canonical = row16['sourceCanonical15']
        require(canonical.count('placement-interpretation=15;') == 1,
                'Source fixture marker is not unique')
        control17.append({
            'seed': row16['seed'],
            'sourceCanonical15': canonical,
            'sourcePlanSha25617': assess._prior_reader().digest(
                canonical.replace('placement-interpretation=15;', 'placement-interpretation=17;')),
            'graph17': row16['graph16'],
            'intent17': row16['intent16'],
            'layoutSeed17': row16['layoutSeed16'],
            'routingSeed17': row16['routingSeed16'],
            'eligibleIds': list(row16['eligibleIds']),
            'densityVersion17': 14,
            'passivePosePolicy17': 2,
            'admissionIdentity17': 'RB56_BOARD_NORMAL@3',
        })
    raw_baselines = assess._read_json('raw-baselines.json')['rows']
    raw_rows = []
    for row in raw_baselines:
        raw_rows.append({
            'seed': row['seed'], 'attempt': row['attempt'],
            'geometry15': row['geometry15'], 'geometry17': row['geometry16'],
            'evaluations15': row['evaluations15'], 'evaluations17': row['evaluations16'],
            'demand15': row['demand15'], 'demand17': row['demand16'],
            'courtyard15': row['courtyard15'], 'courtyard17': row['courtyard16'],
        })
    prefixes = protocol['sourcePrefixes']
    rows = ([line(prefixes['old'], row) for row in old_sources]
            + [line(prefixes['trial16'], row) for row in trial16]
            + [line(prefixes['control17'], row) for row in control17]
            + [line(protocol['rawPrefix'], row) for row in raw_rows])
    return '\n'.join(rows) + '\n', trial16, control17, raw_rows


def build_route_fixture(bindings):
    protocol = assess._protocol()
    route_baselines = assess._read_json('route-baselines.json')['rows']
    pose_baselines = assess._read_json('pose-baselines.json')['rows']
    route_by_case = {(row['seed'], row['version']): copy.deepcopy(row) for row in route_baselines}
    pose_by_case = {(row['seed'], row['version']): copy.deepcopy(row) for row in pose_baselines}
    route_rows, pose_rows = [], []
    for seed, version in (tuple(case) for case in protocol['cases']):
        if version in (15, 16):
            route_rows.append(copy.deepcopy(route_by_case[(seed, version)]))
            pose_rows.append(copy.deepcopy(pose_by_case[(seed, version)]))
            continue
        route = copy.deepcopy(route_by_case[(seed, 16)])
        route['version'] = 17
        route['sourcePlanSha256'] = bindings[seed]['sourcePlanSha25617']
        route['elapsedMillis'] = 8100
        route['width'] += 5
        route['area'] = route['width'] * route['height']
        route['largestProtectedEmptyRectangleArea'] += 100
        route['copperManhattanLength'] += 11
        route['vias'] += 1
        route['geometrySha256'] = ('a' if seed == '42' else 'b') * 64
        route_rows.append(route)
        pose_rows.append({
            'seed': seed, 'issuedAdmissionIdentity': 'RB56_BOARD_NORMAL@3', 'version': 17,
            'top0': list(bindings[seed]['eligibleIds']), 'top90': [],
        })
    return route_rows, pose_rows


def phase_fixture(phase, report_rows, suites=None, timing=None, receipt_edits=None, text_edits=None):
    protocol = assess._protocol()
    suites = suites if suites is not None else assess._phase_manifest(phase)
    pairs = assess._suite_pairs(suites)
    passes = ['PASS: ' + marker + SUITE_DETAILS[name] for name, marker in pairs]
    receipt_lines = list(passes) + list(report_rows)
    text_lines = list(passes) + list(report_rows)
    if receipt_edits:
        receipt_edits(receipt_lines)
    if text_edits:
        text_edits(text_lines)
    count = 8 if phase == 'unit' else 2
    text_lines.extend([assess.TERMINAL.format(count=count), assess.CLEANUP])
    result_timing = {
        'status': 'PASS', 'exitCode': 0, 'elapsedSeconds': 1.25, 'suiteCount': count,
        'phase': phase, 'scope': protocol['timingScope'],
        'terminalCleanupIncluded': True, 'cleanupDuration': 'NOT_SEPARATELY_MEASURED',
    }
    if timing:
        result_timing.update(timing)
    return '\n'.join(text_lines), '\n'.join(receipt_lines), result_timing, suites


def mutated_unit(unit_text, prefix, row_index, key, value):
    lines = unit_text.splitlines()
    indexes = [i for i, item in enumerate(lines) if item.startswith(prefix)]
    record = json.loads(lines[indexes[row_index]][len(prefix):])
    record[key] = value
    lines[indexes[row_index]] = line(prefix, record)
    return '\n'.join(lines) + '\n'


def mutated_route_context(route_rows, pose_rows, route_index=None, route_key=None,
                          route_value=None, pose_index=None, pose_key=None, pose_value=None):
    changed_routes, changed_poses = copy.deepcopy(route_rows), copy.deepcopy(pose_rows)
    if route_index is not None:
        changed_routes[route_index][route_key] = route_value
    if pose_index is not None:
        changed_poses[pose_index][pose_key] = pose_value
    return changed_routes, changed_poses


def record(name, action):
    before = ASSERTIONS
    try:
        action()
    except Exception as failure:
        RESULTS.append({'name': name, 'status': 'FAIL', 'assertions': ASSERTIONS - before,
                        'failureType': type(failure).__name__, 'reason': str(failure)})
    else:
        RESULTS.append({'name': name, 'status': 'PASS', 'assertions': ASSERTIONS - before})


def run_canaries():
    global ASSERTIONS
    unit_text, trial16, control17, raw_rows = build_unit_fixture()
    bindings = assess.check_sources(unit_text)
    checked_raw = assess.check_raw(unit_text, bindings)
    route_rows, pose_rows = build_route_fixture(bindings)
    protocol = assess._protocol()
    unit_report_rows = unit_text.splitlines()
    route_report_rows = []
    for route, pose in zip(route_rows, pose_rows):
        route_report_rows.append(line(protocol['posePrefix'], pose))
        route_report_rows.append(line(protocol['routePrefix'], route))

    def positive_chain():
        result = assess.assess_routes(route_rows, pose_rows, bindings)
        require(len(bindings) == 14, 'Source result does not cover all14 roots')
        require(len(checked_raw) == 15 and len(raw_rows) == 15, 'Raw result does not cover all15 cases')
        require(result['status'] == 'PASS', 'Synthetic valid route chain did not pass')
        require(len(result['old4Reproductions']) == 4, 'Old route reproduction count changed')
        require(len(result['control2DescriptiveDeltas']) == 2, 'Control metric delta count changed')
        require('no non-regression' in result['metricInterpretation'], 'Control deltas were framed as acceptance')
        require(bindings['42']['sourcePlanSha25617'] != bindings['42']['sourcePlanSha25616'],
                '17 canonical digest did not change from the prior16 interpretation')

    record(CASES[0], positive_chain)

    def unit_phase_pass():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        result = assess.check_phase(text, receipt, timing, suites)
        require(result['status'] == 'PASS' and result['suiteCount'] == 8, 'Unit phase result changed')
        require(result['reportOrder'] == ['old'] * 14 + ['trial16'] * 14 + ['control17'] * 14 + ['raw'] * 15,
                'Unit report groups changed')

    record(CASES[1], unit_phase_pass)

    def routes_phase_pass():
        text, receipt, timing, suites = phase_fixture('routes', route_report_rows)
        result = assess.check_phase(text, receipt, timing, suites)
        require(result['status'] == 'PASS' and result['suiteCount'] == 2, 'Route phase result changed')
        require(result['reportOrder'] == [label for _ in protocol['cases'] for label in ('pose', 'route')],
                'Route pose/row order changed')

    record(CASES[2], routes_phase_pass)

    def source_case_order_rejected():
        lines = unit_text.splitlines()
        indexes = [i for i, item in enumerate(lines) if item.startswith(protocol['sourcePrefixes']['control17'])]
        lines[indexes[0]], lines[indexes[1]] = lines[indexes[1]], lines[indexes[0]]
        expect_rejection(lambda: assess.check_sources('\n'.join(lines) + '\n'))

    record(CASES[3], source_case_order_rejected)

    def source_identity_rejections():
        prefix = protocol['sourcePrefixes']['control17']
        for key, value in (('densityVersion17', 13), ('passivePosePolicy17', 1),
                           ('admissionIdentity17', 'RB56_BOARD_NORMAL@2')):
            bad = mutated_unit(unit_text, prefix, 0, key, value)
            expect_rejection(lambda bad=bad: assess.check_sources(bad))

    record(CASES[4], source_identity_rejections)

    def source_graph_and_seed_rejections():
        prefix = protocol['sourcePrefixes']['control17']
        bad_graph = mutated_unit(unit_text, prefix, 0, 'graph17', '0' * 64)
        expect_rejection(lambda: assess.check_sources(bad_graph))
        bad_seed = mutated_unit(unit_text, prefix, 0, 'layoutSeed17', '9223372036854775808')
        expect_rejection(lambda: assess.check_sources(bad_seed))

    record(CASES[5], source_graph_and_seed_rejections)

    def source_digest_and_canonical_rejections():
        prefix = protocol['sourcePrefixes']['control17']
        bad_digest = mutated_unit(unit_text, prefix, 0, 'sourcePlanSha25617', '0' * 64)
        expect_rejection(lambda: assess.check_sources(bad_digest))
        bad_canonical = mutated_unit(unit_text, prefix, 0, 'sourceCanonical15', 'different canonical source')
        expect_rejection(lambda: assess.check_sources(bad_canonical))

    record(CASES[6], source_digest_and_canonical_rejections)

    def raw_change_rejected():
        prefix = protocol['rawPrefix']
        index = next(i for i, row in enumerate(raw_rows) if row['seed'] == '-1022' and row['attempt'] == 4)
        bad = mutated_unit(unit_text, prefix, index, 'evaluations17', raw_rows[index]['evaluations17'] + 1)
        expect_rejection(lambda: assess.check_raw(bad, bindings))

    record(CASES[7], raw_change_rejected)

    def old_route_metric_change_rejected():
        index = next(i for i, row in enumerate(route_rows) if row['seed'] == '3' and row['version'] == 15)
        bad_routes, bad_poses = mutated_route_context(
            route_rows, pose_rows, route_index=index, route_key='copperManhattanLength',
            route_value=route_rows[index]['copperManhattanLength'] + 1)
        expect_rejection(lambda: assess.assess_routes(bad_routes, bad_poses, bindings))

    record(CASES[8], old_route_metric_change_rejected)

    def control_pose_rejections():
        index = next(i for i, row in enumerate(route_rows) if row['seed'] == '42' and row['version'] == 17)
        bad_routes, bad_poses = mutated_route_context(
            route_rows, pose_rows, pose_index=index, pose_key='top90', pose_value=['PASSIVE-X'])
        expect_rejection(lambda: assess.assess_routes(bad_routes, bad_poses, bindings))
        bad_routes, bad_poses = mutated_route_context(
            route_rows, pose_rows, pose_index=index, pose_key='issuedAdmissionIdentity',
            pose_value='RB56_BOARD_NORMAL@2')
        expect_rejection(lambda: assess.assess_routes(bad_routes, bad_poses, bindings))

    record(CASES[9], control_pose_rejections)

    def route_order_rejected():
        bad_routes, bad_poses = copy.deepcopy(route_rows), copy.deepcopy(pose_rows)
        bad_routes[1], bad_routes[2] = bad_routes[2], bad_routes[1]
        expect_rejection(lambda: assess.assess_routes(bad_routes, bad_poses, bindings))

    record(CASES[10], route_order_rejected)

    def phase_report_order_rejected():
        wrong_rows = list(unit_report_rows)
        wrong_rows[0], wrong_rows[14] = wrong_rows[14], wrong_rows[0]
        text, receipt, timing, suites = phase_fixture('unit', wrong_rows)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[11], phase_report_order_rejected)

    def missing_suite_marker_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        target_prefix = 'PASS: ' + suites[2][1]
        text = '\n'.join(line for line in text.splitlines() if not line.startswith(target_prefix))
        receipt = '\n'.join(line for line in receipt.splitlines() if not line.startswith(target_prefix))
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[12], missing_suite_marker_rejected)

    def failed_and_timeout_phase_rejected():
        text, receipt, timing, suites = phase_fixture('routes', route_report_rows,
                                                       timing={'status': 'FAIL', 'exitCode': 1})
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))
        timeout_text, timeout_receipt, timing, suites = phase_fixture('routes', route_report_rows,
            timing={'status': 'TIMEOUT', 'exitCode': 124})
        timeout_text += '\nTimeoutError: native phase timed out'
        expect_rejection(lambda: assess.check_phase(timeout_text, timeout_receipt, timing, suites))

    record(CASES[13], failed_and_timeout_phase_rejected)

    def cleanup_rejections():
        text, receipt, timing, suites = phase_fixture('routes', route_report_rows)
        text = '\n'.join(line for line in text.splitlines() if line != assess.CLEANUP)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))
        text, receipt, timing, suites = phase_fixture('routes', route_report_rows)
        lines = text.splitlines()
        lines.remove(assess.CLEANUP)
        lines.insert(lines.index(assess.TERMINAL.format(count=2)), assess.CLEANUP)
        expect_rejection(lambda: assess.check_phase('\n'.join(lines), receipt, timing, suites))

    record(CASES[14], cleanup_rejections)

    def timing_schema_and_scope_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows,
                                                       timing={'scope': 'unbounded/other'})
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        timing['unexpected'] = 1
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[15], timing_schema_and_scope_rejected)

    def duplicate_suite_marker_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        prefix = 'PASS: ' + suites[0][1]
        for value in ('text', 'receipt'):
            lines = text.splitlines() if value == 'text' else receipt.splitlines()
            index = next(i for i, item in enumerate(lines) if item.startswith(prefix))
            lines.insert(index + 1, lines[index])
            if value == 'text':
                text = '\n'.join(lines)
            else:
                receipt = '\n'.join(lines)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[16], duplicate_suite_marker_rejected)

    def suite_marker_order_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        for value in ('text', 'receipt'):
            lines = text.splitlines() if value == 'text' else receipt.splitlines()
            indexes = [i for i, item in enumerate(lines) if item.startswith('PASS: PCB passive ')]
            lines[indexes[0]], lines[indexes[1]] = lines[indexes[1]], lines[indexes[0]]
            if value == 'text':
                text = '\n'.join(lines)
            else:
                receipt = '\n'.join(lines)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[17], suite_marker_order_rejected)

    def log_receipt_suffix_mismatch_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        text = text.replace('visits=126', 'visits=127', 1)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[18], log_receipt_suffix_mismatch_rejected)

    def unrelated_suite_suffix_rejected():
        text, receipt, timing, suites = phase_fixture('unit', unit_report_rows)
        text = text.replace('visits=126', 'visits=126 unrelated', 1)
        receipt = receipt.replace('visits=126', 'visits=126 unrelated', 1)
        expect_rejection(lambda: assess.check_phase(text, receipt, timing, suites))

    record(CASES[19], unrelated_suite_suffix_rejected)


def consumed_hashes():
    target = HERE
    prior = HERE.parent / 'passive-pose-policy-r1' / 'assess.py'
    published = PUBLISHED_UNIT
    files = {
        'proposal.json': target / 'proposal.json',
        'protocol.json': target / 'protocol.json',
        'unit-suites.json': target / 'unit-suites.json',
        'route-suites.json': target / 'route-suites.json',
        'accepted15-baselines.json': target / 'accepted15-baselines.json',
        'route-baselines.json': target / 'route-baselines.json',
        'raw-baselines.json': target / 'raw-baselines.json',
        'pose-baselines.json': target / 'pose-baselines.json',
        'priorPolicyAssess.py': prior,
        'publishedPolicyUnit.txt': published,
        'assessment-canaries.json': target / 'assessment-canaries.json',
        'assess.py': target / 'assess.py',
        'check_assessment.py': target / 'check_assessment.py',
    }
    return {name: sha256(path.read_bytes()) for name, path in files.items()}


def main():
    run_canaries()
    names = [item['name'] for item in RESULTS]
    manifest = json.loads((HERE / 'assessment-canaries.json').read_text(encoding='utf-8'))
    manifest_names = [item['name'] for item in manifest['cases']]
    if names != manifest_names or names != CASES:
        RESULTS.append({'name': 'canary-manifest-exact-order', 'status': 'FAIL', 'assertions': 1,
                        'failureType': 'AssertionError', 'reason': 'Canary manifest differs from runner case order'})
    all_pass = bool(RESULTS) and all(item['status'] == 'PASS' for item in RESULTS)
    result = {
        'status': 'PASS' if all_pass else 'FAIL',
        'readerCanariesStatus': 'PASS' if all_pass else 'FAIL',
        'nativeExecutionStatus': 'NOT RUN',
        'strict14FitnessStatus': 'NOT RUN; the existing strict14 rejection remains unchanged.',
        'scope': 'Synthetic-only reader canaries; no native execution or new native outputs read.',
        'candidate': 'placement17/density14/passivePosePolicy2; RB56_BOARD_NORMAL@3',
        'syntheticOnly': True,
        'newNativeOutputsRead': False,
        'caseCount': len(RESULTS),
        'passedCases': sum(item['status'] == 'PASS' for item in RESULTS),
        'failedCases': sum(item['status'] != 'PASS' for item in RESULTS),
        'canaryExpectationAssertions': ASSERTIONS,
        'cases': RESULTS,
        'consumedSha256': consumed_hashes(),
        'limits': [
            'Published passive-pose-policy-r1/unit.txt was hash-checked and used only for its trial16 sourceCanonical fixtures.',
            'New17 source/raw/route/pose rows were generated in memory from sealed inputs.',
            'No new unit/routes output, native process, Java/GWT build, browser, or physical solver execution was used.',
            'Reader PASS means protocol/identity/old-control reproduction only; control17 metric deltas are descriptive and do not pass the strict14 fitness oracle.',
        ],
    }
    output = HERE / 'assessment-canary-results.json'
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'caseCount': result['caseCount'],
                      'passedCases': result['passedCases'], 'failedCases': result['failedCases'],
                      'canaryExpectationAssertions': ASSERTIONS}, ensure_ascii=False))
    return 0 if all_pass else 1


if __name__ == '__main__':
    raise SystemExit(main())
