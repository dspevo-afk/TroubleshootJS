#!/usr/bin/env python3
"""Compare the frozen saved-profile baselines with qualified AN and final AO sources.

This successor preserves the historical catalog and pure physical validators.
It binds one explicit 22/23 -> 24/25 -> 25/26 source chain and audits all six
saved-profile cohorts without sealing current geometry or relaxing any bounds.
"""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path

from read_diagnostic import strict_json, need, integer
import read_geometry_current as current_context
import read_geometry_transition as geometry
import read_saved_profile_transition as saved

PROTOCOL = 'p07-final-source-saved-profile-context-v1'
RUNS_SCHEMA = 'p07-final-source-saved-profile-run-spec-v1'
SAVED_HELPER_SHA256 = '390acf30090bd5a3e29b64dcfc212d30fc26c6d1f590d49fd72ebb4d9aa51670'
GEOMETRY_HELPER_SHA256 = '3aa4fa311908c87ba9f68485048fee1d1a2680192e06679eb66c2b68c01779f5'
DIAGNOSTIC_HELPER_SHA256 = '31f65be5e11472c7b09e8141292402c641df6b735787d32eb4c904ae098981e7'
BASELINE_LAYOUT = 22
REFERENCE_LAYOUT = current_context.REFERENCE_LAYOUT
CURRENT_LAYOUT = 25
CURRENT_INPUT_REVISION = 3
MAX_RUNS_BYTES = 4 * 1024 * 1024
MAX_RESULT_BYTES = 1024 * 1024
MAX_NATIVE_LIMIT_MS = 180000
MAX_WRAPPER_LIMIT_MS = 600000
CLEANUP_LINE = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
PASS_LINE = 'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.'


def sha_bytes(raw):
    return hashlib.sha256(raw).hexdigest()


def read_bounded(path, limit, label):
    path = Path(path)
    need(path.is_file(), label + ' is not a regular file')
    size = path.stat().st_size
    need(0 < size <= limit, label + ' exceeds its transport bound')
    return path.read_bytes()


def load_helpers():
    here = Path(__file__).resolve(strict=True).parent
    saved_path = here / 'read_saved_profile_transition.py'
    geometry_path = here / 'read_geometry_transition.py'
    diagnostic_path = here / 'read_diagnostic.py'
    need(sha_bytes(read_bounded(saved_path, 2 * 1024 * 1024, 'historical saved-profile reader')) ==
         SAVED_HELPER_SHA256,
         'historical saved-profile validator bytes changed')
    need(sha_bytes(read_bounded(geometry_path, 2 * 1024 * 1024, 'historical geometry reader')) ==
         GEOMETRY_HELPER_SHA256,
         'historical geometry validator bytes changed')
    need(sha_bytes(read_bounded(diagnostic_path, 1024 * 1024, 'diagnostic JSON helper')) ==
         DIAGNOSTIC_HELPER_SHA256,
         'historical diagnostic helper bytes changed')
    need(Path(saved.__file__).resolve(strict=True) == saved_path and
         Path(geometry.__file__).resolve(strict=True) == geometry_path,
         'historical helper import resolved outside the maintained reader directory')
    return Path(__file__).resolve(strict=True).parents[3]


def load_run_spec(path, expected_sha, source_context_sha, repo_root):
    raw = read_bounded(path, MAX_RUNS_BYTES, 'final-source saved-profile run specification')
    need(type(expected_sha) is str and re.fullmatch(r'[0-9a-f]{64}', expected_sha) is not None and
         sha_bytes(raw) == expected_sha,
         'saved-profile run-spec digest differs from its supplied frozen digest')
    spec = strict_json(raw.decode('utf-8-sig'))
    need(type(spec) is dict and set(spec) == {
        'schema', 'candidateId', 'sourceContextSha256', 'cohorts'},
        'final-source saved-profile run-spec fields differ')
    need(spec.get('schema') == RUNS_SCHEMA and
         spec.get('candidateId') == 'AO_FINAL_LAYOUT25_DEP26' and
         spec.get('sourceContextSha256') == source_context_sha,
         'saved-profile run spec is not bound to the frozen AO source context')
    rows = spec.get('cohorts')
    need(type(rows) is list and len(rows) == len(saved.COHORTS),
         'saved-profile run spec must bind exactly six cohorts')
    by_id = {}
    for expected_cohort, row in zip(saved.COHORTS, rows):
        need(type(row) is dict and set(row) == {'cohortId', 'baseline', 'current'} and
             row.get('cohortId') == expected_cohort['id'] and row['cohortId'] not in by_id,
             'saved-profile cohort identity/order differs from the frozen six-cohort census')
        pair = {}
        for side in ['baseline', 'current']:
            value = row.get(side)
            need(type(value) is dict and set(value) == {
                'receipt', 'result', 'console', 'pins', 'attempt'},
                'saved-profile side must bind receipt/result/console/pins/attempt')
            need(type(value.get('attempt')) is int and 0 < value['attempt'] <= 999,
                 'saved-profile wrapper attempt is invalid')
            pair[side] = {
                key: saved.resolve_input_path(value[key], repo_root,
                    row['cohortId'] + '/' + side + '/' + key)
                for key in ['receipt', 'result', 'console', 'pins']}
            pair[side]['attempt'] = value['attempt']
        by_id[row['cohortId']] = pair
    return spec, by_id, sha_bytes(raw)


def validate_wrapper_context_identity(result, context, layout_version, side):
    expected = {'layoutVersion': layout_version,
                'dependencyEpoch': context['dependencyEpoch']}
    if side == 'baseline':
        for key, value in expected.items():
            if key in result:
                need(type(result[key]) is type(value) and result[key] == value,
                     'baseline wrapper-reported ' + key + ' differs from its frozen source context')
        return {
            key: ('WRAPPER_RESULT' if key in result else 'FROZEN_CONTEXT_AND_NATIVE_FRAME')
            for key in expected
        }
    need(side == 'current' and all(
        type(result.get(key)) is type(value) and result.get(key) == value
        for key, value in expected.items()),
        side + ' wrapper result omits or changes its explicit source-version identity')
    return {key: 'WRAPPER_RESULT' for key in expected}


def read_side(side_paths, cohort, side, context, layout_version,
              catalog_rows, source_texts, arrays_by_source, source_map,
              bound_failures):
    cohort_tag = cohort['id'].removeprefix('SAVED_').lower().replace('_', '-')
    console_name = side_paths['console'].name
    need(re.fullmatch(r'saved-profile-' + re.escape(side) + r'-' +
         re.escape(cohort_tag) + r'-\d+-console\.txt', console_name) is not None,
         side + ' console filename does not identify the selected saved-profile cohort')
    receipt_raw = read_bounded(side_paths['receipt'], saved.MAX_RECEIPT_BYTES,
                               side + ' native receipt')
    console_raw = read_bounded(side_paths['console'], saved.MAX_CONSOLE_BYTES,
                               side + ' native stdout console')
    console_preamble_raw = saved.validate_console_preamble_bytes(console_raw)
    receipt_lines = receipt_raw.decode('utf-8-sig').splitlines()
    console_lines = console_raw.decode('utf-8-sig').splitlines()
    observation = saved.extract_native(receipt_lines, cohort, layout_version, side, console=False)
    console_observation = saved.extract_native(console_lines, cohort, layout_version, side, console=True)
    need(console_observation['nativeLines'] == observation['nativeLines'] and
         console_lines.count(CLEANUP_LINE) == 1,
         side + ' receipt/stdout native content differs or cleanup marker is missing/duplicated')
    need(len(observation['cases']) == 12,
         side + ' native observation is not one complete twelve-case cohort')
    summary = observation['summary']
    summarized_cases = []
    for (version, seed, ordinal), row in zip(saved.cohort_case_order(cohort), observation['cases']):
        catalog_row = catalog_rows[(cohort['id'], version, seed)]
        summarized_cases.append(saved.validate_case(
            row, cohort, ordinal, version, seed, side, catalog_row,
            source_texts, arrays_by_source, layout_version, bound_failures))

    result_raw = read_bounded(side_paths['result'], MAX_RESULT_BYTES,
                               side + ' maintained wrapper result')
    result = strict_json(result_raw.decode('utf-8-sig'))
    expected_revision = context['inputRevision']
    need(type(result) is dict and integer(result.get('exitCode'), 0, 0) and
         result.get('source') == side and result.get('cohortId') == cohort['id'] and
         result.get('suite') == 'P07GeometryEpochDiagnosticContractTest' and
         result.get('inputRevision') == expected_revision and
         result.get('inputCount') == context['inputCount'] and
         result.get('inputPinsSha256') == context['pinsSha256'] and
         result.get('inputAudit') == 'PASS_BEFORE_AND_AFTER' and
         result.get('nativeChildLimitMs') == MAX_NATIVE_LIMIT_MS and
         result.get('outerWatchdogMs') == MAX_WRAPPER_LIMIT_MS and
         result.get('terminationProven') is True and
         result.get('cleanupStatus') in (None, 'PASS_MARKER_REPORTED_ONCE') and
         type(result.get('processId')) is int and result['processId'] > 0 and
         type(result.get('processStartTicks')) is int and result['processStartTicks'] > 0 and
         type(result.get('elapsedSeconds')) in (int, float) and
         0 < result['elapsedSeconds'] <= MAX_WRAPPER_LIMIT_MS / 1000 and
         result['elapsedSeconds'] * 1000 >= summary['nativeDiagnosticElapsedMs'] and
         integer(result.get('stdoutBytes'), 0, saved.MAX_CONSOLE_BYTES) and
         result.get('stdoutBytes') == len(console_raw) and
         result.get('stdoutSha256') == sha_bytes(console_raw) and
         integer(result.get('stderrBytes'), 0, saved.MAX_CONSOLE_BYTES) and
         type(result.get('stderrSha256')) is str and
         re.fullmatch(r'[0-9a-f]{64}', result['stderrSha256']) is not None,
         side + ' wrapper exit/cleanup/process/input audit differs from the selected context')
    wrapper_context_identity = validate_wrapper_context_identity(
        result, context, layout_version, side)
    if 'sourceSnapshotRevision' in result:
        need(result['sourceSnapshotRevision'] == context['sourceSnapshotRevision'],
             side + ' wrapper-reported source snapshot revision differs')
    helper_path = 'scripts/VerifierIsolation.psm1'
    need(helper_path in source_map and
         ('moduleSha256' not in result or result['moduleSha256'] == source_map[helper_path]['sha256']),
         side + ' bounded-process helper module digest differs from source pins')
    run_pin_map, run_pin_raw = current_context.load_pin_manifest(
        side_paths['pins'], context, side + ' cohort source input')
    need(run_pin_map == source_map and sha_bytes(run_pin_raw) == context['pinsSha256'],
         side + ' cohort source-pin manifest differs from its source tree context')

    stderr_path = side_paths['console'].with_name(
        console_name.replace('-console.txt', '-stderr.txt'))
    if result['stderrBytes'] == 0:
        need(result['stderrSha256'] == sha_bytes(b''), side + ' empty stderr digest differs')
        if stderr_path.exists():
            need(stderr_path.is_file() and stderr_path.stat().st_size == 0,
                 side + ' zero-byte stderr file differs from the wrapper result')
    else:
        stderr_raw = read_bounded(stderr_path, saved.MAX_CONSOLE_BYTES, side + ' native stderr')
        need(len(stderr_raw) == result['stderrBytes'] and sha_bytes(stderr_raw) == result['stderrSha256'],
             side + ' captured stderr differs from the wrapper result')

    result_value = {
        'cases': summarized_cases,
        'summary': observation['summary'],
        'result': {
            'source': side, 'exitCode': result['exitCode'],
            'elapsedSeconds': result['elapsedSeconds'],
            'nativeDiagnosticElapsedMs': summary['nativeDiagnosticElapsedMs'],
            'nativeChildLimitMs': result['nativeChildLimitMs'],
            'outerWatchdogMs': result['outerWatchdogMs'],
            'terminationProven': result['terminationProven'],
            'cleanupStatus': result.get('cleanupStatus'),
            'cleanupDurationMs': result.get('cleanupDurationMs'),
            'inputAudit': result['inputAudit'], 'inputCount': result['inputCount'],
            'inputRevision': result['inputRevision'],
            'wrapperContextIdentity': wrapper_context_identity,
            'wrapperReportedLayoutVersion': result.get('layoutVersion'),
            'wrapperReportedDependencyEpoch': result.get('dependencyEpoch'),
            'sourceSnapshotRevision': result.get('sourceSnapshotRevision'),
            'inputPinsSha256': result['inputPinsSha256'],
            'processId': result['processId'], 'processStartTicks': result['processStartTicks'],
            'stdoutBytes': result['stdoutBytes'], 'stdoutSha256': result['stdoutSha256'],
            'stderrBytes': result['stderrBytes'], 'stderrSha256': result['stderrSha256'],
        },
        'receiptSha256': sha_bytes(receipt_raw), 'receiptBytes': len(receipt_raw),
        'consoleSha256': sha_bytes(console_raw), 'consoleBytes': len(console_raw),
        'consolePreambleSha256': sha_bytes(console_preamble_raw),
        'consolePreambleBytes': len(console_preamble_raw),
        'consolePreambleLineCount': len(saved.HOST_PREFIX_LINES),
        'pinFileSha256': sha_bytes(run_pin_raw),
    }
    result_value['_observation'] = observation
    result_value['_receiptLines'] = receipt_lines
    result_value['_consoleLines'] = console_lines
    result_value['_pinMap'] = run_pin_map
    return result_value


def run_current_reader_canaries(cohort, old, new, catalog_raw, catalog_sha,
                                source_context, source_maps):
    checks = 0
    def reject(action, label):
        nonlocal checks
        try:
            action()
        except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError):
            checks += 1
            return
        raise ValueError('final-source saved-profile negative canary accepted: ' + label)

    baseline_context = source_context['contexts']['baseline']
    current_source_context = source_context['contexts']['current']
    need(validate_wrapper_context_identity({}, baseline_context, BASELINE_LAYOUT, 'baseline') == {
        'layoutVersion': 'FROZEN_CONTEXT_AND_NATIVE_FRAME',
        'dependencyEpoch': 'FROZEN_CONTEXT_AND_NATIVE_FRAME'},
        'baseline frozen-context/native-frame identity fallback was rejected')
    need(validate_wrapper_context_identity({
        'layoutVersion': BASELINE_LAYOUT,
        'dependencyEpoch': baseline_context['dependencyEpoch']},
        baseline_context, BASELINE_LAYOUT, 'baseline') == {
            'layoutVersion': 'WRAPPER_RESULT', 'dependencyEpoch': 'WRAPPER_RESULT'},
        'explicit baseline wrapper identity was rejected')
    need(validate_wrapper_context_identity({
        'layoutVersion': CURRENT_LAYOUT,
        'dependencyEpoch': current_source_context['dependencyEpoch']},
        current_source_context, CURRENT_LAYOUT, 'current') == {
            'layoutVersion': 'WRAPPER_RESULT', 'dependencyEpoch': 'WRAPPER_RESULT'},
        'explicit current wrapper identity was rejected')
    reject(lambda: validate_wrapper_context_identity(
        {'layoutVersion': BASELINE_LAYOUT + 1}, baseline_context, BASELINE_LAYOUT, 'baseline'),
        'mismatched baseline layout metadata')
    reject(lambda: validate_wrapper_context_identity(
        {'dependencyEpoch': 'tsj-generation-dependencies-v999'},
        baseline_context, BASELINE_LAYOUT, 'baseline'),
        'mismatched baseline dependency metadata')
    reject(lambda: validate_wrapper_context_identity(
        {'dependencyEpoch': current_source_context['dependencyEpoch']},
        current_source_context, CURRENT_LAYOUT, 'current'),
        'missing current wrapper layout metadata')
    reject(lambda: validate_wrapper_context_identity(
        {'layoutVersion': CURRENT_LAYOUT}, current_source_context, CURRENT_LAYOUT, 'current'),
        'missing current wrapper dependency metadata')

    reject(lambda: saved.extract_native(old['_receiptLines'][:-1], cohort,
                                        BASELINE_LAYOUT, 'baseline'), 'truncated receipt')
    reject(lambda: saved.extract_native(new['_receiptLines'], cohort,
                                        REFERENCE_LAYOUT, 'current'), 'stale layout24 current context')
    bad_cleanup = list(new['_consoleLines'])
    cleanup_index = bad_cleanup.index(CLEANUP_LINE)
    bad_cleanup[cleanup_index] = 'CLEANUP: altered marker'
    reject(lambda: saved.extract_native(bad_cleanup, cohort, CURRENT_LAYOUT,
                                        'current', console=True), 'cleanup marker mutation')
    injected = list(new['_receiptLines'])
    summary_index = next(i for i, line in enumerate(injected) if line.startswith(saved.SUMMARY))
    case_line = next(line for line in injected if line.startswith(saved.CASE))
    injected.insert(summary_index, case_line)
    reject(lambda: saved.extract_native(injected, cohort, CURRENT_LAYOUT,
                                        'current'), 'injected native case')
    mutated_catalog = bytearray(catalog_raw)
    mutated_catalog[len(mutated_catalog) // 2] ^= 1
    reject(lambda: saved.validate_catalog_bytes(bytes(mutated_catalog), catalog_sha),
           'frozen catalog byte mutation')
    wrong_context = copy.deepcopy(source_context)
    wrong_context['contexts']['current']['layoutVersion'] = 24
    reject(lambda: current_context.validate_source_context_object(wrong_context),
           'current layout version mutation')
    wrong_context = copy.deepcopy(source_context)
    wrong_context['contexts']['current']['pinsSha256'] = '0' * 64
    reject(lambda: current_context.validate_source_context_object(wrong_context),
           'current source-pin digest mutation')
    wrong_context = copy.deepcopy(source_context)
    wrong_context['contexts']['current']['sourceSnapshotRevision'] += 1
    reject(lambda: current_context.validate_source_context_object(wrong_context),
           'current source-snapshot revision mutation')
    wrong_context = copy.deepcopy(source_context)
    wrong_context['contexts']['current']['inputCount'] -= 1
    reject(lambda: current_context.validate_source_context_object(wrong_context),
           'current source input-count mutation')
    wrong_transition = copy.deepcopy(source_context['referenceToCurrent'])
    wrong_transition['commonChangedPaths'] = sorted(
        set(wrong_transition['commonChangedPaths']) | {'src/unknown/Unpinned.java'})
    reject(lambda: current_context.validate_transition_spec_against_maps(
        wrong_transition, source_maps['reference'], source_maps['current']),
        'unbound current source path')
    wrong_transition = copy.deepcopy(source_context['referenceToCurrent'])
    wrong_transition['commonChangedPaths'] = sorted(
        (set(wrong_transition['commonChangedPaths']) -
         {'tests/contracts/PcbCompactnessContractTest.java'}) |
        {'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java'})
    wrong_context = copy.deepcopy(source_context)
    wrong_context['referenceToCurrent'] = wrong_transition
    reject(lambda: current_context.validate_source_context_object(wrong_context),
           'unreviewed physical source path substitution')
    bad_row = copy.deepcopy(new['cases'][0])
    bad_row['layoutVersion'] = REFERENCE_LAYOUT
    version, seed, ordinal = saved.cohort_case_order(cohort)[0]
    reject(lambda: saved.validate_case(
        bad_row, cohort, ordinal, version, seed, 'current',
        {}, {}, {}, CURRENT_LAYOUT), 'current case layout mutation')
    return checks


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-context', type=Path, required=True)
    parser.add_argument('--source-context-sha256', required=True)
    parser.add_argument('--runs', type=Path, required=True)
    parser.add_argument('--runs-sha256', required=True)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--catalog-sha256', required=True)
    parser.add_argument('--baseline-pins', type=Path, required=True)
    parser.add_argument('--reference-pins', type=Path, required=True)
    parser.add_argument('--current-pins', type=Path, required=True)
    parser.add_argument('--previous-current-pins', type=Path, required=True)
    parser.add_argument('--baseline-inputs', type=Path, required=True)
    parser.add_argument('--reference-inputs', type=Path, required=True)
    parser.add_argument('--current-inputs', type=Path, required=True)
    parser.add_argument('--previous-current-inputs', type=Path, required=True)
    parser.add_argument('--baseline-normalizer', type=Path, required=True)
    parser.add_argument('--reference-normalizer', type=Path, required=True)
    parser.add_argument('--current-normalizer', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    need(not args.output.exists(), 'refusing to overwrite an existing final-source saved-profile output')
    repo_root = load_helpers()
    source_context, source_context_raw, source_context_sha = current_context.load_source_context(
        args.source_context, args.source_context_sha256)
    runs_spec, run_paths, runs_sha = load_run_spec(
        args.runs, args.runs_sha256, source_context_sha, repo_root)

    contexts = source_context['contexts']
    paths = {'baseline': (args.baseline_pins, args.baseline_inputs),
             'reference': (args.reference_pins, args.reference_inputs),
             'current': (args.current_pins, args.current_inputs)}
    source_maps, manifest_raws, source_trees, source_roots = {}, {}, {}, {}
    for side in ['baseline', 'reference', 'current']:
        source_maps[side], manifest_raws[side] = current_context.load_pin_manifest(
            paths[side][0], contexts[side], side)
        source_trees[side] = current_context.audit_source_tree(
            paths[side][1], source_maps[side], side)
        source_roots[side] = Path(paths[side][1]).resolve(strict=True)
    transitions = current_context.validate_source_transitions(source_context, source_maps)
    previous_current_map, previous_current_root = current_context.load_previous_current_source_inputs(
        args.previous_current_pins, args.previous_current_inputs)
    previous_current_diagnostic = current_context.validate_previous_current_diagnostic_helper(
        previous_current_root, previous_current_map,
        source_roots['current'], source_maps['current'])
    normalizers = current_context.validate_normalizer_chain(source_roots, source_maps)
    diagnostic_boundary = current_context.validate_diagnostic_source_chain(source_roots, source_maps)
    compactness_source = current_context.validate_compactness_hashes(source_maps, source_roots)
    normalizer_paths = {'baseline': args.baseline_normalizer,
                        'reference': args.reference_normalizer,
                        'current': args.current_normalizer}
    for side in ['baseline', 'reference', 'current']:
        expected_path = source_roots[side] / 'tests/contracts/PcbCompactnessContractTest.java'
        need(normalizer_paths[side].resolve(strict=True) == expected_path.resolve(strict=True),
             side + ' normalizer path differs from its exact source tree')

    catalog_raw = read_bounded(args.catalog, saved.MAX_CATALOG_BYTES, 'frozen baseline catalog')
    catalog = saved.validate_catalog_bytes(catalog_raw, args.catalog_sha256)
    need(args.catalog_sha256 == saved.CATALOG_SHA256,
         'saved-profile catalog hash differs from the preserved historical catalog')
    baseline_pin_map = {key: value['sha256'] for key, value in source_maps['baseline'].items()}
    source_texts, arrays_by_source, catalog_bindings = saved.load_source_catalog(
        source_roots['baseline'], catalog, baseline_pin_map)
    catalog_rows = saved.validate_catalog_rows(catalog, source_texts, arrays_by_source)

    bound_failures = []
    cohort_outputs = []
    side_results = {}
    first_canary_pair = None
    for cohort in saved.COHORTS:
        pair = {}
        for side, layout in [('baseline', BASELINE_LAYOUT), ('current', CURRENT_LAYOUT)]:
            context = contexts[side]
            pair[side] = read_side(
                run_paths[cohort['id']][side], cohort, side, context, layout,
                catalog_rows, source_texts, arrays_by_source, source_maps[side], bound_failures)
        old = pair['baseline']
        new = pair['current']
        need(old['pinFileSha256'] == contexts['baseline']['pinsSha256'] and
             new['pinFileSha256'] == contexts['current']['pinsSha256'],
             'per-cohort source pin file differs from the global source context')
        need(old['consolePreambleSha256'] == new['consolePreambleSha256'] and
             old['consolePreambleBytes'] == new['consolePreambleBytes'],
             'baseline/current console preambles differ within a cohort')
        comparisons = saved.compare_logical_cases(old['cases'], new['cases'], cohort)
        cohort_outputs.append({
            'cohortId': cohort['id'],
            'baseline': {key: old[key] for key in [
                'summary', 'result', 'receiptSha256', 'receiptBytes', 'consoleSha256',
                'consoleBytes', 'consolePreambleSha256', 'consolePreambleBytes',
                'consolePreambleLineCount', 'pinFileSha256']},
            'current': {key: new[key] for key in [
                'summary', 'result', 'receiptSha256', 'receiptBytes', 'consoleSha256',
                'consoleBytes', 'consolePreambleSha256', 'consolePreambleBytes',
                'consolePreambleLineCount', 'pinFileSha256']},
            'cases': comparisons,
        })
        side_results[cohort['id']] = {
            'baseline': {'cases': old['cases']},
            'current': {'cases': new['cases']},
        }
        if first_canary_pair is None:
            first_canary_pair = (cohort, old, new)

    dynamic_checks = {
        side: saved.compare_dynamic_saved10_areas(
            side_results, saved.COHORTS, catalog_rows, side, bound_failures)
        for side in ['baseline', 'current']
    }
    dynamic_items = [item for group in dynamic_checks.values() for item in group]
    need(len(cohort_outputs) == 6 and
         all(len(item['cases']) == 12 for item in cohort_outputs) and
         len(dynamic_items) == 20,
         'final-source saved-profile reader did not evaluate all72 cases and twenty paired bounds')
    dynamic_pass = all(item.get('status') == 'PASS_SAME_SIDE_SAVED10_OBSERVATION'
                       for item in dynamic_items)

    exact_metric_changes, exact_geometry_changes = [], []
    for cohort_output in cohort_outputs:
        for case in cohort_output['cases']:
            identity = {'cohortId': cohort_output['cohortId'],
                        'caseOrdinal': case['caseOrdinal'],
                        'placementInterpretationVersion': case['placementInterpretationVersion'],
                        'rootSeed': case['rootSeed']}
            for bound in case['currentAreaSourceBounds']:
                if bound.get('status') == 'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW':
                    exact_metric_changes.append({**identity, 'sourceId': bound['sourceId'],
                        'metric': bound['metric'], 'operator': bound['operator'],
                        'literal': bound['literal'], 'expected': bound['expected'],
                        'actual': bound['actual'], 'status': bound['status']})
            if case['currentOldSourceHashStatus'] == 'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW':
                exact_geometry_changes.append({**identity,
                    'sourceId': 'EXTERNAL_SAVED_PROFILE_CATALOG',
                    'metric': 'normalizedPhysicalSha256', 'operator': '==',
                    'literal': 'oldHash', 'expected': case['baselineNormalizedPhysicalSha256'],
                    'actual': case['currentNormalizedPhysicalSha256'],
                    'status': case['currentOldSourceHashStatus']})

    canary_count = run_current_reader_canaries(
        first_canary_pair[0], first_canary_pair[1], first_canary_pair[2],
        catalog_raw, args.catalog_sha256, source_context, source_maps)
    failures = len(bound_failures)
    status = 'FAIL_RETAINED_BOUNDS' if failures else 'PASS_COMPARISON_ONLY'
    exit_code = 2 if failures else 0
    result = {
        'protocol': PROTOCOL,
        'status': status,
        'exitCode': exit_code,
        'scope': 'FRESH_FINAL_SOURCE_FULL_SIX_COHORT_SAVED_PROFILE_COMPARISON',
        'layoutQualification': False,
        'adopted': False,
        'adoption': False,
        'automaticGoldenSeal': False,
        'physicalAuditScope': 'ALL_SIX_COHORTS_72_CASES_PER_SOURCE; RETAINED_BOUNDS_AND_INDEPENDENT_PHYSICAL_ORACLES_EVALUATED',
        'retainedBoundFailuresCount': failures,
        'retainedBoundFailures': bound_failures,
        'cohortSelection': 'ALL_SIX_COHORTS',
        'cohortCount': len(cohort_outputs),
        'caseCountPerSource': sum(len(item['cases']) for item in cohort_outputs),
        'baselineLayoutVersion': BASELINE_LAYOUT,
        'baselineDependencyEpoch': contexts['baseline']['dependencyEpoch'],
        'referenceLayoutVersion': current_context.REFERENCE_LAYOUT,
        'referenceDependencyEpoch': contexts['reference']['dependencyEpoch'],
        'currentLayoutVersion': CURRENT_LAYOUT,
        'currentDependencyEpoch': contexts['current']['dependencyEpoch'],
        'baselineInputRevision': contexts['baseline']['inputRevision'],
        'referenceInputRevision': contexts['reference']['inputRevision'],
        'currentInputRevision': contexts['current']['inputRevision'],
        'sourceContextSha256': source_context_sha,
        'runSpecSha256': runs_sha,
        'catalogSha256': sha_bytes(catalog_raw),
        'sourceInputTransition': {
            'baselineCount': contexts['baseline']['inputCount'],
            'referenceCount': contexts['reference']['inputCount'],
            'currentCount': contexts['current']['inputCount'],
            'baselinePinsSha256': contexts['baseline']['pinsSha256'],
            'referencePinsSha256': contexts['reference']['pinsSha256'],
            'currentPinsSha256': contexts['current']['pinsSha256'],
            'transitions': transitions,
            'previousCurrentDiagnosticHelper': previous_current_diagnostic,
            'sourceTrees': source_trees,
            'currentSourceSnapshotRevision': contexts['current']['sourceSnapshotRevision'],
        },
        'referenceQualificationEvidenceSha256': source_context['referenceQualification'],
        'baselineSourceCatalogBinding': {
            'sourceRootConsistent': True,
            'sourceExpectationsReadFrom': 'FROZEN_BASELINE_INPUT_SNAPSHOT',
            'sourceFiles': catalog_bindings,
        },
        'normalizationBoundary': normalizers,
        'diagnosticSourceBoundary': diagnostic_boundary,
        'compactnessSourceBoundary': compactness_source,
        'sameSideSaved10Comparisons': dynamic_checks,
        'sameSideSaved10ComparisonCount': len(dynamic_items),
        'sameSideSaved10ComparisonsComplete': True,
        'sameSideSaved10ComparisonsPassed': dynamic_pass,
        'currentExactMetricReview': {
            'status': 'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW'
                if exact_metric_changes else 'NO_CURRENT_EXACT_METRIC_CHANGES',
            'required': bool(exact_metric_changes), 'changes': exact_metric_changes,
            'automaticGoldenSeal': False,
        },
        'currentExactGeometryReview': {
            'status': 'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW'
                if exact_geometry_changes else 'NO_CURRENT_EXACT_GEOMETRY_CHANGES',
            'required': bool(exact_geometry_changes), 'changes': exact_geometry_changes,
            'automaticGoldenSeal': False,
        },
        'negativeCanaries': canary_count,
        'cohorts': cohort_outputs,
        'limits': [
            'The complete comparison contains six frozen cohorts and72 cases per source; no larger population is inferred.',
            'Current exact source geometry differences are reported for review; no current hash or area bound is auto-sealed.',
            'All results remain observational; actual100/admission, production timing, player repair, and publication are not qualified.',
            'Cleanup evidence is the maintained single marker and wrapper termination proof; no missing cleanup duration is inferred.',
        ],
        'readerSha256': sha_bytes(Path(__file__).read_bytes()),
        'helperSha256': {
            'read_diagnostic.py': DIAGNOSTIC_HELPER_SHA256,
            'read_geometry_transition.py': GEOMETRY_HELPER_SHA256,
            'read_saved_profile_transition.py': SAVED_HELPER_SHA256,
            'read_geometry_current.py': sha_bytes(Path(__file__).with_name('read_geometry_current.py').read_bytes()),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2, ensure_ascii=True, allow_nan=False) + '\n')
    if exit_code:
        print('FAIL: retained saved-profile bounds; status=' + status +
              '; failures=' + str(failures) + '; cases/source=72; layout qualification=false; adoption=false')
    else:
        print('PASS: final-source saved-profile comparison only; 72 cases/source; layout qualification=false; adoption=false')
    return exit_code


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
