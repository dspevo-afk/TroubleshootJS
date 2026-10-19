"""Synthetic positive and mutation canaries for the passive-pose evidence reader.

These fixtures exercise parser and gate behavior only. They contain no native
policy observations and make no physical-layout or solver claim.
"""
import copy
import hashlib
import json
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / 'assessment-canaries.json').read_text(encoding='utf-8'))
EXPECTED_NAMES = [item['id'] for group in ('positiveCases', 'readerRejections', 'gateOutcomes')
                  for item in MANIFEST[group]]
completed = []
assertions = 0


def check(value, message):
    global assertions
    assertions += 1
    if not value:
        raise AssertionError(message)


def record(case_id):
    check(case_id not in completed, 'Canary case repeated: ' + case_id)
    completed.append(case_id)


def reject(case_id, action):
    try:
        action()
    except (AssertionError, KeyError, TypeError, ValueError):
        record(case_id)
        return
    raise AssertionError('Expected reader rejection did not occur: ' + case_id)


def status_case(case_id, action, expected):
    result = action()
    check(result['status'] == expected, case_id + ' expected ' + expected + ', got ' + result['status'])
    record(case_id)


def synthetic_source_rows(old):
    rows, source_baselines = [], []
    package_order = sorted(assess.PASSIVES)
    for root_index, baseline in enumerate(old):
        component_count = baseline['parts']
        pad_count = baseline['pads']
        extra_pads = pad_count - component_count
        pieces, eligible = [], []
        for component_index in range(component_count):
            component_id = 'S%02d_C%02d' % (root_index, component_index)
            package = package_order[component_index] if component_index < len(package_order) else 'OTHER_PACKAGE'
            if package in assess.PASSIVES:
                eligible.append(component_id)
            header = ':'.join((component_id, 'Synthetic', 'component', package,
                               'DEFAULT', 'domain', 'region'))
            pads = 1 + (extra_pads if component_index == 0 else 0)
            pieces.append(header + ''.join('|%s.P%d' % (component_id, pad)
                                           for pad in range(pads)))
        canonical = 'placement-interpretation=15;seed=' + baseline['seed'] + ';' + '[' + ']['.join(pieces) + ']'
        source_baseline = dict(baseline)
        source_baseline['sourcePlanSha256'] = assess.digest(canonical)
        derived = assess.digest(canonical.replace('placement-interpretation=15;',
                                                    'placement-interpretation=16;'))
        row = {
            'seed': baseline['seed'],
            'sourceCanonical15': canonical,
            'sourcePlanSha25616': derived,
            'graph16': baseline['liveBoardGraphSha256'],
            'intent16': baseline['sourceIntentSha256'],
            'layoutSeed16': baseline['layoutSeed'],
            'routingSeed16': baseline['routingSeed'],
            'eligibleIds': sorted(eligible),
        }
        rows.append(row)
        source_baselines.append(source_baseline)
    return rows, source_baselines


def synthetic_bindings(old):
    result = {}
    for index, baseline in enumerate(old):
        seed = baseline['seed']
        ids = ['Q%02d_P%d' % (index, item) for item in range(4)]
        result[seed] = {'seed': seed,
            'sourcePlanSha25616': assess.digest('synthetic-trial-source:' + seed),
            'eligibleIds': ids}
    return result


def synthetic_raw_rows():
    result = []
    for seed in ('42', '-1022', '4'):
        for attempt in (0, 2, 3, 4, 5):
            geometry = assess.digest('synthetic-geometry:%s:%d' % (seed, attempt))
            evaluations = 1000 + attempt
            demand = 200000 + attempt
            courtyard = 100000 + attempt
            result.append({'seed': seed, 'attempt': attempt,
                'geometry15': geometry, 'geometry16': geometry,
                'evaluations15': evaluations, 'evaluations16': evaluations,
                'demand15': demand, 'demand16': demand,
                'courtyard15': courtyard, 'courtyard16': courtyard})
    return result


def synthetic_routes(old, bindings, improve=True):
    routes, poses = [], []
    for version in (15, 16):
        for root_index, baseline in enumerate(old):
            row = copy.deepcopy(baseline)
            row['elapsedMillis'] = 123
            row['version'] = version
            if version == 16:
                row['sourcePlanSha256'] = bindings[baseline['seed']]['sourcePlanSha25616']
                row['geometrySha256'] = assess.digest('synthetic-routed-geometry:' + baseline['seed'])
                if improve and root_index == 0:
                    row['width'] -= 1
                    row['area'] = row['width'] * row['height']
            routes.append(row)
            ids = list(bindings[baseline['seed']]['eligibleIds'])
            turned = [ids[0]] if version == 16 and baseline['seed'] == '42' else []
            poses.append({'seed': baseline['seed'],
                'issuedAdmissionIdentity': 'RB56_BOARD_NORMAL@1' if version == 15 else 'RB56_BOARD_NORMAL@2',
                'version': version, 'top0': sorted(set(ids) - set(turned)), 'top90': turned})
    return routes, poses


def synthetic_phase(phase):
    suites = json.loads(assess.phase_suites_path(phase).read_text(encoding='utf-8'))
    lines = []
    if phase == 'unit':
        for name, marker in suites:
            if name == 'PcbPassivePoseSourceContractTest':
                for index in range(14):
                    lines.append('PCB_PASSIVE_POSE_SOURCE_CASE ' + json.dumps({'syntheticRoot': index}))
            if name == 'PcbPassivePoseTrialSourceContractTest':
                for index in range(14):
                    lines.append('PCB_PASSIVE_POLICY_INPUT ' + json.dumps({'syntheticRoot': index}))
            if name in ('PcbPassivePoseRawContractTest', 'PcbPassivePoseRawContractTestGroup2',
                        'PcbPassivePoseRawContractTestGroup3'):
                for index in range(5):
                    lines.append('PCB_PASSIVE_POLICY_RAW ' + json.dumps({'syntheticBranch': index}))
            lines.append('PASS: ' + marker + 'synthetic=PASS')
    else:
        for index, (name, marker) in enumerate(suites):
            version = 15 if index < 14 else 16
            lines.append('PCB_PASSIVE_POLICY_POSES ' + json.dumps({'syntheticCase': index, 'version': version}))
            lines.append('PCB_PASSIVE_POLICY_ROUTE ' + json.dumps({'syntheticCase': index, 'version': version}))
            lines.append('PASS: ' + marker + 'synthetic=PASS')
    receipt = '\n'.join(lines) + '\n'
    terminal = assess.TERMINAL.format(count=len(suites))
    text = receipt + terminal + '\n' + assess.CLEANUP + '\n'
    timing = {'status': 'PASS', 'exitCode': 0, 'elapsedSeconds': 1.25,
        'suiteCount': len(suites), 'phase': phase, 'scope': assess.TIMING_SCOPE,
        'terminalCleanupIncluded': True, 'cleanupDuration': 'NOT_SEPARATELY_MEASURED'}
    return text, receipt, timing, suites


def phase_edit(case_id, phase, edit_text=None, edit_receipt=None, edit_timing=None, edit_suites=None):
    text, receipt, timing, suites = synthetic_phase(phase)
    if edit_text is not None:
        text = edit_text(text)
    if edit_receipt is not None:
        receipt = edit_receipt(receipt)
    if edit_timing is not None:
        timing = edit_timing(timing)
    if edit_suites is not None:
        suites = edit_suites(suites)
    reject(case_id, lambda: assess.check_phase(text, receipt, timing, suites))


def trial_index(seed):
    return 14 + assess.ROOTS.index(seed)


def main():
    old = assess.baselines()
    source_rows, synthetic_old = synthetic_source_rows(old)
    bindings = assess.inputs(source_rows, synthetic_old)
    check(len(bindings) == 14 and list(bindings) == assess.ROOTS, 'Complete ordered synthetic source binding set')
    record('P01_source_bindings14_synthetic')

    raw_rows = synthetic_raw_rows()
    assess.raw(raw_rows)
    check(len(raw_rows) == 15, 'All15 untouched candidate rows accepted')
    record('P02_raw_candidates15_synthetic')

    route_rows, pose_rows = synthetic_routes(old, synthetic_bindings(old))
    route_bindings = synthetic_bindings(old)
    result = assess.assess(route_rows, pose_rows, old, route_bindings)
    check(result['status'] == 'PASS' and result['saved15ExactCases'] == 14 and
          result['trial16Cases'] == 14 and result['regressions'] == [] and
          len(result['areaOrVoidImprovements']) == 1 and result['selectedTurnCases'] == ['42'],
          'Complete synthetic 28-row route/census assessment')
    record('P03_ordered_route_census28_synthetic')

    text, receipt, timing, suites = synthetic_phase('unit')
    unit_result = assess.check_phase(text, receipt, timing, suites)
    check(unit_result['status'] == 'PASS' and unit_result['suiteCount'] == 8,
          'Synthetic unit phase accepts exact8 suite sequence')
    record('P04_unit_phase8_receipt_synthetic')
    text, receipt, timing, suites = synthetic_phase('routes')
    route_phase_result = assess.check_phase(text, receipt, timing, suites)
    check(route_phase_result['status'] == 'PASS' and route_phase_result['suiteCount'] == 28,
          'Synthetic route phase accepts exact28 suite sequence')
    record('P05_routes_phase28_receipt_synthetic')

    bad = copy.deepcopy(source_rows)
    reject('R01_missing_source_binding', lambda: assess.inputs(bad[:-1], synthetic_old))
    bad = copy.deepcopy(source_rows)
    bad[0], bad[1] = bad[1], bad[0]
    reject('R02_reordered_source_binding', lambda: assess.inputs(bad, synthetic_old))
    bad = copy.deepcopy(source_rows)
    bad[0]['unexpected'] = 'field'
    reject('R03_extra_source_field', lambda: assess.inputs(bad, synthetic_old))
    bad = copy.deepcopy(source_rows)
    bad[1]['seed'] = bad[0]['seed']
    reject('R04_duplicate_source_root', lambda: assess.inputs(bad, synthetic_old))
    bad = copy.deepcopy(source_rows)
    bad[0]['sourceCanonical15'] += 'unbound'
    reject('R05_unbound_source_canonical', lambda: assess.inputs(bad, synthetic_old))
    bad = copy.deepcopy(source_rows)
    bad[0]['sourceCanonical15'] = bad[0]['sourceCanonical15'].replace(
        'placement-interpretation=15;', 'placement-interpretation=15;placement-interpretation=15;')
    synthetic_old_bad = copy.deepcopy(synthetic_old)
    synthetic_old_bad[0]['sourcePlanSha256'] = assess.digest(bad[0]['sourceCanonical15'])
    reject('R06_nonunique_interpretation_marker', lambda: assess.inputs(bad, synthetic_old_bad))
    bad = copy.deepcopy(source_rows)
    bad[0]['eligibleIds'].pop()
    reject('R07_eligible_passive_census_drift', lambda: assess.inputs(bad, synthetic_old))

    bad = copy.deepcopy(raw_rows)
    reject('R08_raw_candidate_missing', lambda: assess.raw(bad[:-1]))
    bad = copy.deepcopy(raw_rows)
    bad[0], bad[1] = bad[1], bad[0]
    reject('R09_raw_candidate_reordered', lambda: assess.raw(bad))
    bad = copy.deepcopy(raw_rows)
    bad[0]['geometry16'] = assess.digest('changed raw geometry')
    reject('R10_raw_geometry_changed', lambda: assess.raw(bad))
    bad = copy.deepcopy(raw_rows)
    bad[0]['demand16'] += 1
    reject('R11_raw_work_demand_changed', lambda: assess.raw(bad))

    route_bindings = synthetic_bindings(old)
    route_rows, pose_rows = synthetic_routes(old, route_bindings)
    bad_routes = copy.deepcopy(route_rows)
    reject('R12_route_case_missing', lambda: assess.assess(bad_routes[:-1], pose_rows, old, route_bindings))
    bad_routes = copy.deepcopy(route_rows)
    bad_routes[0], bad_routes[1] = bad_routes[1], bad_routes[0]
    reject('R13_route_order_changed', lambda: assess.assess(bad_routes, pose_rows, old, route_bindings))
    bad_routes = copy.deepcopy(route_rows)
    bad_routes[0]['vias'] += 1
    reject('R14_saved15_metric_changed', lambda: assess.assess(bad_routes, pose_rows, old, route_bindings))
    bad_routes = copy.deepcopy(route_rows)
    bad_routes[trial_index('42')]['sourcePlanSha256'] = assess.digest('wrong binding')
    reject('R15_trial_source_binding_changed', lambda: assess.assess(bad_routes, pose_rows, old, route_bindings))
    bad_routes = copy.deepcopy(route_rows)
    bad_routes[trial_index('42')]['width'] += 1
    reject('R16_trial_area_dimension_mismatch', lambda: assess.assess(bad_routes, pose_rows, old, route_bindings))
    bad_poses = copy.deepcopy(pose_rows)
    bad_poses[trial_index('42')]['top0'].pop()
    reject('R17_pose_census_lost_component', lambda: assess.assess(route_rows, bad_poses, old, route_bindings))
    bad_poses = copy.deepcopy(pose_rows)
    bad_poses[0]['top90'] = [bad_poses[0]['top0'].pop()]
    reject('R18_saved15_TOP90_census', lambda: assess.assess(route_rows, bad_poses, old, route_bindings))
    bad_poses = copy.deepcopy(pose_rows)
    bad_poses[trial_index('42')]['top90'].append(bad_poses[trial_index('42')]['top90'][0])
    reject('R19_duplicate_pose_component', lambda: assess.assess(route_rows, bad_poses, old, route_bindings))

    phase_edit('R20_unit_phase_suite_order_changed', 'unit',
        edit_text=lambda value: _swap_passes(value, 'unit'),
        edit_receipt=lambda value: _swap_passes(value, 'unit'))
    phase_edit('R21_routes_phase_suite_order_changed', 'routes',
        edit_text=lambda value: _swap_passes(value, 'routes'),
        edit_receipt=lambda value: _swap_passes(value, 'routes'))
    phase_edit('R22_phase_terminal_suite_count_changed', 'unit',
        edit_text=lambda value: value.replace('8 Java suites', '7 Java suites'))
    phase_edit('R23_phase_missing_cleanup', 'routes',
        edit_text=lambda value: value.replace(assess.CLEANUP + '\n', ''))
    phase_edit('R24_phase_failure_marker', 'unit',
        edit_text=lambda value: 'FAIL: synthetic rejection canary\n' + value)
    phase_edit('R25_phase_nonzero_exit', 'routes',
        edit_timing=lambda value: dict(value, exitCode=1))
    phase_edit('R26_phase_zero_elapsed', 'unit',
        edit_timing=lambda value: dict(value, elapsedSeconds=0))
    phase_edit('R27_phase_missing_receipt_suite', 'routes',
        edit_receipt=lambda value: _remove_last_pass(value))
    phase_edit('R28_phase_receipt_report_mismatch', 'unit',
        edit_receipt=lambda value: value.replace('syntheticRoot": 0', 'syntheticRoot": 99', 1))
    phase_edit('R29_phase_suite_manifest_substitution', 'unit',
        edit_suites=lambda value: list(reversed(value)))
    phase_edit('R30_phase_timing_suite_count_changed', 'routes',
        edit_timing=lambda value: dict(value, suiteCount=27))
    phase_edit('R31_phase_nonfinite_timing', 'unit',
        edit_timing=lambda value: dict(value, elapsedSeconds=float('nan')))
    phase_edit('R32_phase_duplicate_suite_pass', 'routes',
        edit_text=lambda value: _duplicate_first_pass(value, 'routes'))
    phase_edit('R33_phase_missing_terminal', 'unit',
        edit_text=lambda value: value.replace(assess.TERMINAL.format(count=8) + '\n', ''))
    phase_edit('R34_phase_failure_in_child_receipt', 'routes',
        edit_receipt=lambda value: 'ERROR: synthetic child failure\n' + value)
    phase_edit('R35_phase_unknown_suite_set', 'unit',
        edit_suites=lambda value: value[:-1])

    bad_poses = copy.deepcopy(pose_rows)
    bad_poses[trial_index('42')]['top90'] = ['foreign-component']
    bad_poses[trial_index('42')]['top0'] = sorted(route_bindings['42']['eligibleIds'][1:])
    reject('R36_unknown_active_pose_identity', lambda: assess.assess(route_rows, bad_poses, old, route_bindings))
    bad_routes = copy.deepcopy(route_rows)
    bad_routes[trial_index('42')]['density'] = 13
    reject('R37_trial_density_changed', lambda: assess.assess(bad_routes, pose_rows, old, route_bindings))
    phase_edit('R38_phase_source_case_count_changed', 'unit',
        edit_text=lambda value: _remove_first_prefixed_row(value, 'PCB_PASSIVE_POSE_SOURCE_CASE '),
        edit_receipt=lambda value: _remove_first_prefixed_row(value, 'PCB_PASSIVE_POSE_SOURCE_CASE '))
    phase_edit('R39_current_contract_failure_prefix', 'unit',
        edit_text=lambda value: 'CURRENT_CONTRACT_FAILURE: synthetic expected rejection\n' + value)
    phase_edit('R40_current_contract_cleanup_prefix', 'routes',
        edit_receipt=lambda value: 'CURRENT_CONTRACT_CLEANUP: synthetic incomplete cleanup\n' + value)

    for case_id, metric in (('G01_area_regression', 'area'),
                            ('G02_protected_void_regression', 'largestProtectedEmptyRectangleArea'),
                            ('G03_copper_regression', 'copperManhattanLength'),
                            ('G04_via_regression', 'vias')):
        changed = copy.deepcopy(route_rows)
        route = changed[trial_index('42')]
        if metric == 'area':
            route['width'] += 1
            route['area'] = route['width'] * route['height']
        else:
            route[metric] += 1
        status_case(case_id, lambda changed=changed: assess.assess(changed, pose_rows, old, route_bindings), 'FAIL')

    no_improvement, same_poses = synthetic_routes(old, route_bindings, improve=False)
    status_case('G05_no_strict_area_or_void_improvement',
        lambda: assess.assess(no_improvement, same_poses, old, route_bindings), 'FAIL')
    no_turn = copy.deepcopy(pose_rows)
    for index in range(14, 28):
        no_turn[index]['top0'] = sorted(route_bindings[no_turn[index]['seed']]['eligibleIds'])
        no_turn[index]['top90'] = []
    status_case('G06_no_selected_TOP90_is_inconclusive',
        lambda: assess.assess(route_rows, no_turn, old, route_bindings), 'INCONCLUSIVE')

    check(completed == EXPECTED_NAMES, 'Executed canary set/order differs from manifest')
    positive = len(MANIFEST['positiveCases'])
    rejected = len(MANIFEST['readerRejections'])
    gates = len(MANIFEST['gateOutcomes'])
    results = {
        'schemaVersion': 1,
        'status': 'PASS',
        'scope': 'Synthetic reader-only canaries; no native policy outputs consumed.',
        'nativePolicyOutputsConsumed': False,
        'assertions': assertions,
        'caseCount': len(completed),
        'positiveCaseCount': positive,
        'readerRejectionCount': rejected,
        'gateOutcomeCount': gates,
        'caseIds': {
            'positive': [item['id'] for item in MANIFEST['positiveCases']],
            'readerRejections': [item['id'] for item in MANIFEST['readerRejections']],
            'gateOutcomes': [item['id'] for item in MANIFEST['gateOutcomes']],
        },
        'shapeCounts': {'sourceBindings': 14, 'rawCandidateBranches': 15,
                        'savedRoutes': 14, 'trialRoutes': 14, 'orderedRouteCases': 28,
                        'poseCensuses': 28, 'unitSuites': 8, 'routeSuites': 28},
        'runner': 'Private Python pointer with -X utf8 -B; no executable path recorded.',
        'sha256': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                   for name in ('assess.py', 'check_assessment.py', 'assessment-canaries.json',
                                'accepted15-baselines.json', 'unit-suites.json', 'route-suites.json')},
    }
    (HERE / 'assessment-canary-results.json').write_text(
        json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print('PASS: PCB passive pose assessment canaries assertions=%d cases=%d positive=%d readerRejections=%d gateOutcomes=%d' %
          (assertions, len(completed), positive, rejected, gates))
    print('Synthetic reader-only evidence; no native policy outputs or physical validation consumed.')


def _swap_passes(text, phase):
    lines = text.splitlines()
    pass_indexes = [index for index, line in enumerate(lines)
                    if line.startswith('PASS: PCB passive pose ')]
    if len(pass_indexes) >= 2:
        first, second = pass_indexes[0], pass_indexes[1]
        lines[first], lines[second] = lines[second], lines[first]
    return '\n'.join(lines) + '\n'


def _remove_last_pass(text):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines)
               if line.startswith('PASS: PCB passive pose ')]
    if indexes:
        del lines[indexes[-1]]
    return '\n'.join(lines) + '\n'


def _duplicate_first_pass(text, phase):
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith('PASS: PCB passive pose '):
            lines.insert(index + 1, line)
            break
    return '\n'.join(lines) + '\n'


def _remove_first_prefixed_row(text, prefix):
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith(prefix):
            del lines[index]
            break
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    manifest_case_ids = [item['id'] for group in ('positiveCases', 'readerRejections', 'gateOutcomes')
                         for item in MANIFEST[group]]
    check(len(manifest_case_ids) == len(set(manifest_case_ids)), 'Manifest has duplicate case identifiers')
    main()
