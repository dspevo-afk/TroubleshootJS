import ast
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import read_fixed_prefix_default_parity as parity


def _source_context(root, files):
    root = Path(root)
    pin_map = {}
    pin_bytes = {}
    for relative, raw in files.items():
        path = root.joinpath(*relative.split('/'))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
        pin_map[relative] = hashlib.sha256(raw).hexdigest()
        pin_bytes[relative] = len(raw)
    return {'root': root, 'pinMap': pin_map, 'pinBytes': pin_bytes}


def _delta_fixture(baseline_root, candidate_root, *, extra_common=False):
    paths = sorted(set(parity.UNCHANGED_ORACLES + parity.EXPECTED_CHANGED_INPUTS))
    if extra_common:
        paths.append('src/com/lushprojects/circuitjs1/client/Unexpected.java')
    baseline_files = {
        path: ('baseline:' + path + '\nsecond line\n').encode('utf-8')
        for path in paths
    }
    candidate_files = dict(baseline_files)
    for path in parity.EXPECTED_CHANGED_INPUTS:
        candidate_files[path] = ('candidate:' + path + '\nsecond line\n').encode('utf-8')
    for path in parity.EXPECTED_ADDED_INPUTS:
        candidate_files[path] = ('added:' + path + '\n').encode('utf-8')
    return (_source_context(baseline_root, baseline_files),
            _source_context(candidate_root, candidate_files),
            baseline_files, candidate_files)


def _synthetic_native_lines(*, dependency_epoch=parity.DEPENDENCY_EPOCH, stale_at=None):
    cohort = parity.saved.COHORTS[0]
    run_id = '11111111-1111-1111-1111-111111111111'
    v26 = 'tsj-generation-dependencies-v26'
    current_dependency = dependency_epoch
    frame_dependency = v26 if stale_at == 'frame' else current_dependency
    summary_dependency = v26 if stale_at == 'summary' else current_dependency
    frame = {
        'runId': run_id,
        'protocol': parity.saved.PROTOCOL,
        'cohortId': cohort['id'],
        'caseCount': 12,
        'caseOrdinalBase': 1,
        'caseOrder': 'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER',
        'placementVersions': cohort['versions'],
        'rootSeeds': cohort['seeds'],
        'layoutVersion': parity.LAYOUT_VERSION,
        'dependencyEpoch': frame_dependency,
        'scope': parity.saved.SCOPE,
        'layoutQualification': False,
        'adopted': False,
        'adoption': False,
        'baselineExpected': 'EXTERNAL_SAVED_PROFILE_CATALOG',
    }
    summary = {
        'protocol': parity.saved.PROTOCOL,
        'runId': run_id,
        'cohortId': cohort['id'],
        'cases': 12,
        'caseCount': 12,
        'caseOrder': 'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER',
        'placementVersions': cohort['versions'],
        'rootSeeds': cohort['seeds'],
        'assertions': 1,
        'nativeDiagnosticElapsedMs': 0,
        'timingScope': 'NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING',
        'layoutVersion': parity.LAYOUT_VERSION,
        'dependencyEpoch': summary_dependency,
        'scope': parity.saved.SCOPE,
        'layoutQualification': False,
        'adopted': False,
        'adoption': False,
        'baselineExpected': 'EXTERNAL_SAVED_PROFILE_CATALOG',
    }
    lines = [parity.saved.FRAME + json.dumps(frame, separators=(',', ':'))]
    for version, seed, ordinal in parity.saved.cohort_case_order(cohort):
        row_dependency = (v26 if stale_at == 'case' and ordinal == 1 else current_dependency)
        row = {
            'runId': run_id,
            'frameId': '00000000-0000-0000-0000-{0:012d}'.format(ordinal),
            'layoutVersion': parity.LAYOUT_VERSION,
            'dependencyEpoch': row_dependency,
            'cohortId': cohort['id'],
            'nativeDiagnosticElapsedMs': 0,
            'largestProtectedEmptyRectangleArea': None if version == 3 else 1,
        }
        if version >= 4:
            row['expectedPackages'] = 1
            whitespace = {
                'seed': seed,
                'version': version,
                'scope': ('GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;'
                          'FREE_IS_NOT_PACKABLE_PROOF'),
                'gridUnits': 10,
                'parts': 1,
                'sampledCells': 1,
                'packageAccessOccupiedCells': 0,
                'protectedFreeCells': 1,
                'protectedFreeFraction': 1.0,
                'largestProtectedEmptyRectangle': {
                    'x': 0, 'y': 0, 'width': 1, 'height': 1, 'area': 1,
                },
            }
            lines.append('PCB_WHITESPACE_CASE ' +
                         json.dumps(whitespace, separators=(',', ':')))
        lines.append(parity.saved.CASE + json.dumps(row, separators=(',', ':')))
    lines.extend([
        parity.saved.SUMMARY + json.dumps(summary, separators=(',', ':')),
        (parity.saved.FOOTER + 'cohortId=' + cohort['id'] + ' assertions=1 cases=12 layout=25 ' +
         'dependency=' + current_dependency + ' layoutQualification=false adoption=false scope=' +
         parity.saved.SCOPE),
    ])
    return cohort, lines


def _identity_only_case_row(cohort, ordinal, version, seed, dependency):
    return {
        'protocol': parity.saved.PROTOCOL,
        'status': 'PASS_OBSERVATION_ONLY',
        'scope': parity.saved.SCOPE,
        'cohortId': cohort['id'],
        'caseOrdinal': ordinal,
        'caseCount': 12,
        'caseOrder': 'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER',
        'rootSeed': seed,
        'placementInterpretationVersion': version,
        'placementInterpretation': 'SAVED_PROFILE_' + str(version),
        'layoutVersion': parity.LAYOUT_VERSION,
        'dependencyEpoch': dependency,
        'planVersion': 1,
        'modelDumpEpoch': 'circuitjs-source-load-model-inputs-no-transient-dump-v7',
        'layoutQualification': False,
        'adopted': False,
        'adoption': False,
        'baselineExpected': 'EXTERNAL_SAVED_PROFILE_CATALOG',
        'baselineExpectedBasis': 'INDEPENDENT_SOURCE_DERIVED_CATALOG_READER',
        'runId': '11111111-1111-1111-1111-111111111111',
        'frameId': '00000000-0000-0000-0000-000000000001',
    }


class FixedPrefixDefaultParityContracts(unittest.TestCase):
    def test_source_baseline_and_candidate_are_bound_to_current_v27(self):
        self.assertEqual(parity.ACCEPTED_BASELINE_COMMIT,
                         '9a6736e12487d8f6e7ea15ccfecb8ecaa0c1c295')
        self.assertEqual(parity.DEPENDENCY_EPOCH, 'tsj-generation-dependencies-v27')
        self.assertEqual(parity.SOURCE_SNAPSHOT_REVISIONS,
                         {'baseline': 1, 'candidate': 3})

    def test_reader_and_imported_oracles_are_pinned_to_reviewed_bytes(self):
        digests = parity.validate_helper_sources()
        self.assertEqual(digests['read_saved_profile_transition.py'],
                         parity.SAVED_PROFILE_HELPER_SHA256)
        self.assertEqual(digests['read_diagnostic.py'], parity.DIAGNOSTIC_HELPER_SHA256)
        self.assertRegex(digests['read_fixed_prefix_default_parity.py'], r'^[0-9a-f]{64}$')

    def test_current_metadata_adapters_are_isolated_exact_two_assignment_patches(self):
        saved_module = parity.saved
        saved_namespace = saved_module.__dict__
        saved_items = dict(saved_namespace)
        original_validate = saved_module.validate_case
        original_extract = saved_module.extract_native
        source_path = Path(saved_module.__file__)
        source_raw = source_path.read_bytes()
        self.assertEqual(hashlib.sha256(source_raw).hexdigest(),
                         parity.SAVED_PROFILE_HELPER_SHA256)

        adapters, proof = parity.current_saved_profile_metadata_adapters(
            parity.validate_helper_sources())

        self.assertEqual(proof, {
            'sourceSha256': parity.SAVED_PROFILE_HELPER_SHA256,
            'functionNames': ['validate_case', 'extract_native'],
            'dependencyAssignmentsPatched': 2,
            'onlyMetadataExpressionsChanged': True,
            'globalsIsolated': True,
        })
        self.assertIs(adapters['validate_case'].__globals__,
                      adapters['extract_native'].__globals__)
        self.assertIsNot(adapters['validate_case'].__globals__, saved_namespace)
        self.assertIs(saved_module.validate_case, original_validate)
        self.assertIs(saved_module.extract_native, original_extract)
        self.assertIs(original_validate.__globals__, saved_namespace)
        self.assertIs(original_extract.__globals__, saved_namespace)
        self.assertNotIn('_current_saved_profile_dependency', saved_namespace)
        self.assertEqual(set(saved_namespace), set(saved_items))
        self.assertTrue(all(saved_namespace[key] is value
                            for key, value in saved_items.items()))
        self.assertEqual(source_path.read_bytes(), source_raw)

    def test_current_metadata_adapter_rejects_changed_source_or_assignment_shape(self):
        source_path = Path(parity.saved.__file__)
        source_raw = source_path.read_bytes()
        with self.assertRaises(ValueError):
            parity._compile_saved_profile_metadata_adapters(source_raw + b'\n', source_path)

        tree = ast.parse(source_raw.decode('utf-8'), filename=str(source_path))
        validate_node = next(node for node in tree.body
                             if isinstance(node, ast.FunctionDef) and
                             node.name == 'validate_case')
        assignment = next(node for node in ast.walk(validate_node)
                          if isinstance(node, ast.Assign) and
                          any(isinstance(target, ast.Name) and target.id == 'dependency'
                              for target in node.targets))

        changed = copy.deepcopy(validate_node)
        changed_assignment = next(node for node in ast.walk(changed)
                                  if isinstance(node, ast.Assign) and
                                  any(isinstance(target, ast.Name) and target.id == 'dependency'
                                      for target in node.targets))
        changed_assignment.value = ast.Constant(value='tsj-generation-dependencies-v27')
        with self.assertRaises(ValueError):
            parity._patch_saved_metadata_assignment(changed)

        duplicated = copy.deepcopy(validate_node)
        duplicated.body.insert(1, copy.deepcopy(assignment))
        with self.assertRaises(ValueError):
            parity._patch_saved_metadata_assignment(duplicated)

        renamed = copy.deepcopy(validate_node)
        renamed.name = 'validate_case_unexpected'
        with self.assertRaises(ValueError):
            parity._patch_saved_metadata_assignment(renamed)

    def test_current_v27_native_frame_and_rows_pass_while_v26_metadata_fails(self):
        adapters, _ = parity.current_saved_profile_metadata_adapters(
            parity.validate_helper_sources())
        cohort, current_lines = _synthetic_native_lines()
        observation = adapters['extract_native'](
            current_lines, cohort, parity.LAYOUT_VERSION, 'synthetic', console=False)
        self.assertEqual(observation['frame']['dependencyEpoch'], parity.DEPENDENCY_EPOCH)
        self.assertEqual(observation['summary']['dependencyEpoch'], parity.DEPENDENCY_EPOCH)
        self.assertEqual(len(observation['cases']), 12)
        self.assertTrue(all(row['dependencyEpoch'] == parity.DEPENDENCY_EPOCH
                            for row in observation['cases']))

        historical_lines = _synthetic_native_lines(
            dependency_epoch='tsj-generation-dependencies-v26')[1]
        historical = parity.saved.extract_native(
            historical_lines, cohort, parity.LAYOUT_VERSION, 'historical-v26', console=False)
        self.assertEqual(historical['frame']['dependencyEpoch'],
                         'tsj-generation-dependencies-v26')
        self.assertTrue(all(row['dependencyEpoch'] == 'tsj-generation-dependencies-v26'
                            for row in historical['cases']))
        with self.assertRaises(ValueError):
            parity.saved.extract_native(
                current_lines, cohort, parity.LAYOUT_VERSION, 'frozen-reader-v27', console=False)

        for stale_at in ('frame', 'summary', 'case'):
            with self.subTest(stale_at=stale_at):
                _, stale_lines = _synthetic_native_lines(stale_at=stale_at)
                with self.assertRaises(ValueError):
                    adapters['extract_native'](
                        stale_lines, cohort, parity.LAYOUT_VERSION,
                        'stale-v26-' + stale_at, console=False)

    def test_current_case_identity_guard_accepts_v27_and_rejects_v26(self):
        adapters, _ = parity.current_saved_profile_metadata_adapters(
            parity.validate_helper_sources())
        cohort = parity.saved.COHORTS[0]
        version, seed, ordinal = parity.saved.cohort_case_order(cohort)[0]
        adapter = adapters['validate_case']
        namespace = adapter.__globals__
        original_need = namespace['need']

        class CapturedIdentityGuard(Exception):
            pass

        def first_guard_result(dependency):
            row = _identity_only_case_row(cohort, ordinal, version, seed, dependency)
            observed = []

            def capture(condition, label):
                observed.append((condition, label))
                raise CapturedIdentityGuard()

            namespace['need'] = capture
            try:
                with self.assertRaises(CapturedIdentityGuard):
                    adapter(row, cohort, ordinal, version, seed, 'current',
                            {}, {}, {}, parity.LAYOUT_VERSION, [])
            finally:
                namespace['need'] = original_need
            self.assertEqual(len(observed), 1)
            self.assertIn('case identity, scope, or epoch', observed[0][1])
            return observed[0][0]

        self.assertTrue(first_guard_result(parity.DEPENDENCY_EPOCH))
        self.assertFalse(first_guard_result('tsj-generation-dependencies-v26'))
        self.assertEqual(parity._current_saved_profile_dependency(25), parity.DEPENDENCY_EPOCH)
        for invalid_layout in (24, True):
            with self.subTest(invalid_layout=invalid_layout):
                with self.assertRaises(ValueError):
                    parity._current_saved_profile_dependency(invalid_layout)

    def test_command_binding_requires_exact_suite_and_cohort_environment(self):
        command = parity.expected_command(parity.SUITE_GEOMETRY, 'SAVED_7_8_REP')
        parity.validate_command(command, parity.SUITE_GEOMETRY,
                                'SAVED_7_8_REP', 'fixture')
        changed = copy.deepcopy(command)
        changed['environment']['TSJ_GEOMETRY_COHORT'] = 'SAVED_9_10_REP'
        with self.assertRaises(ValueError):
            parity.validate_command(changed, parity.SUITE_GEOMETRY,
                                    'SAVED_7_8_REP', 'fixture')
        changed = copy.deepcopy(command)
        changed['args'] = ['--baseline']
        with self.assertRaises(ValueError):
            parity.validate_command(changed, parity.SUITE_GEOMETRY,
                                    'SAVED_7_8_REP', 'fixture')

    def test_source_delta_allows_only_the_three_expected_production_changes(self):
        expected = {'changed': parity.EXPECTED_CHANGED_INPUTS,
                    'added': parity.EXPECTED_ADDED_INPUTS, 'removed': []}
        with tempfile.TemporaryDirectory() as temp:
            baseline, candidate, _, _ = _delta_fixture(
                Path(temp) / 'baseline', Path(temp) / 'candidate')
            result = parity.validate_source_delta(baseline, candidate, expected)
            self.assertEqual(result['changed'], expected['changed'])
            self.assertEqual(result['added'], expected['added'])
            self.assertEqual(result['removed'], [])
            self.assertEqual(result['rawChanged'], expected['changed'])
            self.assertEqual(result['lineEndingOnly'], [])

            unexpected_path = 'src/com/lushprojects/circuitjs1/client/GeneratedExtra.java'
            unexpected_raw = b'unexpected\n'
            bad_root = Path(temp) / 'candidate-with-extra'
            actual_candidate = {
                path: (candidate['root'].joinpath(*path.split('/')).read_bytes())
                for path in candidate['pinMap']
            }
            actual_candidate[unexpected_path] = unexpected_raw
            unexpected = _source_context(bad_root, actual_candidate)
            bad_expected = {'changed': expected['changed'],
                            'added': sorted(expected['added'] + [unexpected_path]),
                            'removed': []}
            with self.assertRaises(ValueError):
                parity.validate_source_delta(baseline, unexpected, bad_expected)

    def test_source_delta_reports_complete_crlf_only_differences(self):
        expected = {'changed': parity.EXPECTED_CHANGED_INPUTS,
                    'added': parity.EXPECTED_ADDED_INPUTS, 'removed': []}
        selected = parity.UNCHANGED_ORACLES[0]
        with tempfile.TemporaryDirectory() as temp:
            baseline_files = {
                path: ('baseline:' + path + '\nsecond line\n').encode('utf-8')
                for path in sorted(set(parity.UNCHANGED_ORACLES +
                                       parity.EXPECTED_CHANGED_INPUTS))
            }
            candidate_files = dict(baseline_files)
            for path in parity.EXPECTED_CHANGED_INPUTS:
                candidate_files[path] = ('candidate:' + path + '\nsecond line\n').encode('utf-8')
            for path in parity.EXPECTED_ADDED_INPUTS:
                candidate_files[path] = ('added:' + path + '\n').encode('utf-8')
            candidate_files[selected] = candidate_files[selected].replace(b'\n', b'\r\n')
            baseline = _source_context(Path(temp) / 'lf', baseline_files)
            candidate = _source_context(Path(temp) / 'crlf', candidate_files)
            result = parity.validate_source_delta(baseline, candidate, expected)
            self.assertEqual(result['changed'], expected['changed'])
            self.assertEqual(result['rawChanged'], sorted(expected['changed'] + [selected]))
            self.assertEqual([row['path'] for row in result['lineEndingOnly']], [selected])
            row = result['lineEndingOnly'][0]
            self.assertNotEqual(row['baselineSha256'], row['candidateSha256'])
            self.assertEqual(row['baselineCrlfCount'], 0)
            self.assertGreater(row['baselineLfCount'], 0)
            self.assertGreater(row['candidateCrlfCount'], 0)
            self.assertEqual(row['candidateLfCount'], 0)
            self.assertEqual(row['commonLfSha256'],
                             hashlib.sha256(baseline_files[selected]).hexdigest())

    def test_source_delta_rejects_whitespace_and_content_changes(self):
        expected = {'changed': parity.EXPECTED_CHANGED_INPUTS,
                    'added': parity.EXPECTED_ADDED_INPUTS, 'removed': []}
        oracle = parity.UNCHANGED_ORACLES[0]
        with tempfile.TemporaryDirectory() as temp:
            baseline, candidate, _, candidate_files = _delta_fixture(
                Path(temp) / 'baseline', Path(temp) / 'candidate')
            candidate_files[oracle] += b' \n'
            whitespace_candidate = _source_context(Path(temp) / 'whitespace', candidate_files)
            with self.assertRaises(ValueError):
                parity.validate_source_delta(baseline, whitespace_candidate, expected)

            baseline, candidate, _, candidate_files = _delta_fixture(
                Path(temp) / 'baseline2', Path(temp) / 'candidate2', extra_common=True)
            unexpected = 'src/com/lushprojects/circuitjs1/client/Unexpected.java'
            candidate_files[unexpected] += b'changed content\n'
            content_candidate = _source_context(Path(temp) / 'content', candidate_files)
            with self.assertRaises(ValueError):
                parity.validate_source_delta(baseline, content_candidate, expected)

            baseline, _, _, candidate_files = _delta_fixture(
                Path(temp) / 'baseline3', Path(temp) / 'candidate3')
            candidate_files[oracle] = candidate_files[oracle].replace(b'\n', b'\r')
            lone_cr_candidate = _source_context(Path(temp) / 'lone-cr', candidate_files)
            with self.assertRaises(ValueError):
                parity.validate_source_delta(baseline, lone_cr_candidate, expected)

    def test_source_oracle_change_is_rejected_even_if_declared(self):
        changed_oracle = parity.UNCHANGED_ORACLES[0]
        expected = {'changed': sorted([changed_oracle] + parity.EXPECTED_CHANGED_INPUTS),
                    'added': parity.EXPECTED_ADDED_INPUTS, 'removed': []}
        with tempfile.TemporaryDirectory() as temp:
            baseline, _, _, candidate_files = _delta_fixture(
                Path(temp) / 'baseline', Path(temp) / 'candidate')
            candidate_files[changed_oracle] += b'changed content\n'
            candidate = _source_context(Path(temp) / 'changed-oracle', candidate_files)
            with self.assertRaises(ValueError):
                parity.validate_source_delta(baseline, candidate, expected)

    def test_case_comparison_ignores_only_run_ids_and_native_timing(self):
        baseline = {
            'runId': 'baseline-run', 'frameId': 'baseline-frame',
            'nativeDiagnosticElapsedMs': 100,
            'normalizedPhysicalSha256': '1' * 64,
            'traceGeometryFingerprint': 'same-traces', 'boardArea': 1000,
            'policyStatisticsCanonical': 'routes=2',
        }
        candidate = dict(baseline, runId='candidate-run', frameId='candidate-frame',
                         nativeDiagnosticElapsedMs=125)
        self.assertTrue(parity.compare_case_payloads(baseline, candidate))
        parity.require_case_parity(baseline, candidate, 'fixture')
        candidate['normalizedPhysicalSha256'] = '2' * 64
        with self.assertRaises(ValueError):
            parity.require_case_parity(baseline, candidate, 'geometry mutation')
        candidate = dict(baseline)
        candidate['boardArea'] += 1
        with self.assertRaises(ValueError):
            parity.require_case_parity(baseline, candidate, 'metric mutation')
        candidate = dict(baseline, newlyAddedNullField=None)
        with self.assertRaises(ValueError):
            parity.require_case_parity(baseline, candidate, 'null field addition')
        baseline_with_null = dict(baseline, optionalField=None)
        with self.assertRaises(ValueError):
            parity.require_case_parity(baseline_with_null, baseline, 'null field removal')

    def test_frame_and_summary_keep_census_and_policy_bindings_exact(self):
        left = {
            'frame': {'runId': 'r1', 'cohortId': 'SAVED_3_4_REP',
                      'caseCount': 12, 'layoutVersion': 25},
            'summary': {'runId': 'r1', 'cases': 12, 'assertions': 100,
                        'nativeDiagnosticElapsedMs': 1000},
            'footer': 'PASS: fixed cohort',
        }
        right = {
            'frame': {'runId': 'r2', 'cohortId': 'SAVED_3_4_REP',
                      'caseCount': 12, 'layoutVersion': 25},
            'summary': {'runId': 'r2', 'cases': 12, 'assertions': 100,
                        'nativeDiagnosticElapsedMs': 1300},
            'footer': 'PASS: fixed cohort',
        }
        self.assertTrue(parity.compare_frame_and_summary(left, right))
        right['summary']['cases'] = 11
        self.assertFalse(parity.compare_frame_and_summary(left, right))

    def test_compactness_payload_requires_both_six_hash_tables(self):
        hashes = [{'rootSeed': seed, 'sha256': ('a' if index % 2 == 0 else 'b') * 64}
                  for index, seed in enumerate(parity.COMPACTNESS_SEEDS)]
        criteria = {
            'mode': 'report', 'scope': 'FIXED_ACTUAL_RB56_GEOMETRY_REPORT_ONLY',
            'compactGoldenAppliesTo': 'report', 'rootSeeds': parity.COMPACTNESS_SEEDS,
            'packages': [40, 44, 60], 'pads': [96, 104, 143],
            'routingPolicy': 'MEDIUM_BOARD@1', 'admission': 'RB56_BOARD_NORMAL@1',
            'densityThreshold': None,
            'currentLayout25Compact2ExpectedHashes': hashes,
            'currentLayout25Legacy1ExpectedHashes': hashes,
        }
        cases = [{'mode': 'report', 'rootSeed': seed,
                  'placementInterpretationVersion': 2,
                  'routePolicy': 'P07_FULLER_TWO_LAYER',
                  'copperClearanceValidated': True,
                  'admission': 'RB56_BOARD_NORMAL@1'}
                 for seed in parity.COMPACTNESS_SEEDS]
        parity.validate_compactness_payload(criteria, cases, 'fixture')
        changed = copy.deepcopy(criteria)
        changed['currentLayout25Legacy1ExpectedHashes'][2]['sha256'] = 'bad'
        with self.assertRaises(ValueError):
            parity.validate_compactness_payload(changed, cases, 'mutated literal')
        changed = copy.deepcopy(criteria)
        changed['currentLayout25Compact2ExpectedHashes'][0]['unexpected'] = 'extra'
        with self.assertRaises(ValueError):
            parity.validate_compactness_payload(changed, cases, 'mutated shape')
        changed_cases = copy.deepcopy(cases)
        changed_cases[0]['copperClearanceValidated'] = 'true'
        with self.assertRaises(ValueError):
            parity.validate_compactness_payload(criteria, changed_cases, 'string boolean')

    def test_compactness_completion_summary_binds_three_report_cases(self):
        summary = [
            'PASS: actual RB56 geometry report 843 assertions cases=3 mode=report '
            'scope=NO_DENSITY_ACCEPTANCE_THRESHOLD'
        ]
        self.assertEqual(parity.validate_compactness_summary_lines(summary, 'fixture'), 843)
        for changed in [
                summary + summary,
                ['PASS: actual RB56 geometry report 0 assertions cases=3 mode=report '
                 'scope=NO_DENSITY_ACCEPTANCE_THRESHOLD'],
                ['PASS: actual RB56 geometry report 843 assertions cases=2 mode=report '
                 'scope=NO_DENSITY_ACCEPTANCE_THRESHOLD']]:
            with self.assertRaises(ValueError):
                parity.validate_compactness_summary_lines(changed, 'fixture')

    def test_complete_census_requires_all_six_cohorts_and_all_twenty_bounds(self):
        counts = {cohort: 12 for cohort in parity.COHORT_IDS}
        expected_dynamic = [
            (cohort['id'], seed)
            for cohort in parity.saved.COHORTS
            for version, seed, _ in parity.saved.cohort_case_order(cohort)
            if version == 11
        ]
        comparisons = {
            side: [{
                'cohortId': cohort_id, 'rootSeed': seed,
                'status': 'PASS_SAME_SIDE_SAVED10_OBSERVATION', 'operator': '<=',
                'saved10Area': 100, 'saved11Area': 90,
            } for cohort_id, seed in expected_dynamic]
            for side in ['baseline', 'candidate']
        }
        parity.validate_complete_saved_census(counts, comparisons, [])
        missing = dict(comparisons)
        missing['candidate'] = comparisons['candidate'][:-1]
        with self.assertRaises(ValueError):
            parity.validate_complete_saved_census(counts, missing, [])
        failed = copy.deepcopy(comparisons)
        failed['baseline'][0]['status'] = 'FAIL_RETAINED_BOUND'
        with self.assertRaises(ValueError):
            parity.validate_complete_saved_census(counts, failed, [])
        duplicate = copy.deepcopy(comparisons)
        duplicate['candidate'][1] = dict(duplicate['candidate'][0])
        with self.assertRaises(ValueError):
            parity.validate_complete_saved_census(counts, duplicate, [])

    def test_source_review_keeps_historical_hash_status_separate_from_bounds(self):
        rows = [
            {
                'sourceSide': 'baseline',
                'oldHashStatus': 'CURRENT_EXACT_GEOMETRY_MATCH_NOT_ADOPTED',
                'boardAreaBounds': [{'status': 'PASS'}],
                'otherSourceBounds': [{'status': 'PASS'}],
            },
            {
                'sourceSide': 'candidate',
                'oldHashStatus': 'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW',
                'boardAreaBounds': [
                    {'status': 'PASS'},
                    {'status': 'DEFERRED_TO_SAME_SIDE_SAVED10_OBSERVATION'},
                ],
                'otherSourceBounds': [],
            },
        ]
        counts = parity.source_review_status_counts(rows)
        self.assertEqual(counts['baseline']['historicalExactHashStatusCounts'], {
            'CURRENT_EXACT_GEOMETRY_MATCH_NOT_ADOPTED': 1})
        self.assertEqual(counts['baseline']['retainedBoardAreaStatusCounts'], {'PASS': 1})
        self.assertEqual(counts['candidate']['historicalExactHashStatusCounts'], {
            'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW': 1})
        self.assertEqual(counts['candidate']['retainedBoardAreaStatusCounts'], {
            'DEFERRED_TO_SAME_SIDE_SAVED10_OBSERVATION': 1, 'PASS': 1})

    def test_native_result_requires_numeric_pid_and_string_start_ticks(self):
        command = parity.expected_command(parity.SUITE_COMPACTNESS)
        raw = b'captured output\n'
        empty = b''
        context = {
            'sourceSnapshotRevision': 1, 'inputCount': 4, 'inputBytes': 10,
            'pinsSha256': 'a' * 64,
        }
        result = {
            'schema': 'p07-fixed-prefix-native-run-v1', 'source': 'baseline',
            'suite': parity.SUITE_COMPACTNESS, 'cohortId': None, 'command': command,
            'layoutVersion': 25, 'dependencyEpoch': parity.DEPENDENCY_EPOCH,
            'sourceSnapshotRevision': 1, 'inputCount': 4, 'inputBytes': 10,
            'inputPinsSha256': 'a' * 64, 'inputAudit': 'PASS_BEFORE_AND_AFTER',
            'exitCode': 0, 'nativeExitCode': 0, 'terminationProven': True,
            'cleanupStatus': 'PASS', 'cleanupElapsedMs': None,
            'nativeLimitMs': 60000, 'outerLimitMs': 1800000,
            'processId': 1234, 'processStartTicks': '987654321',
            'elapsedSeconds': 1.25, 'requestedExecutable': 'powershell.exe',
            'stdoutBytes': len(raw), 'stdoutSha256': parity.sha_bytes(raw),
            'stderrBytes': 0, 'stderrSha256': parity.sha_bytes(empty),
            'receiptBytes': 0, 'receiptSha256': parity.sha_bytes(empty),
        }
        parity.validate_result(result, side_name='baseline', cohort_id=None,
                               suite=parity.SUITE_COMPACTNESS, command=command,
                               source_context=context, stdout_raw=raw, stderr_raw=empty)
        bad = dict(result, processId='1234')
        with self.assertRaises(ValueError):
            parity.validate_result(bad, side_name='baseline', cohort_id=None,
                                   suite=parity.SUITE_COMPACTNESS, command=command,
                                   source_context=context, stdout_raw=raw, stderr_raw=empty)

    def test_native_results_reject_stale_v26_for_both_source_sides(self):
        command = parity.expected_command(parity.SUITE_COMPACTNESS)
        raw = b'captured output\n'
        empty = b''
        base_context = {
            'sourceSnapshotRevision': 1, 'inputCount': 4, 'inputBytes': 10,
            'pinsSha256': 'a' * 64,
        }
        for side_name, revision in (('baseline', 1), ('candidate', 3)):
            context = dict(base_context, sourceSnapshotRevision=revision)
            result = {
                'schema': 'p07-fixed-prefix-native-run-v1', 'source': side_name,
                'suite': parity.SUITE_COMPACTNESS, 'cohortId': None,
                'command': command, 'layoutVersion': parity.LAYOUT_VERSION,
                'dependencyEpoch': parity.DEPENDENCY_EPOCH,
                'sourceSnapshotRevision': revision, 'inputCount': 4,
                'inputBytes': 10, 'inputPinsSha256': 'a' * 64,
                'inputAudit': 'PASS_BEFORE_AND_AFTER', 'exitCode': 0,
                'nativeExitCode': 0, 'terminationProven': True,
                'cleanupStatus': 'PASS', 'cleanupElapsedMs': None,
                'nativeLimitMs': 60000, 'outerLimitMs': 1800000,
                'processId': 1234, 'processStartTicks': '987654321',
                'elapsedSeconds': 1.25, 'requestedExecutable': 'powershell.exe',
                'stdoutBytes': len(raw), 'stdoutSha256': parity.sha_bytes(raw),
                'stderrBytes': 0, 'stderrSha256': parity.sha_bytes(empty),
                'receiptBytes': 0, 'receiptSha256': parity.sha_bytes(empty),
            }
            parity.validate_result(
                result, side_name=side_name, cohort_id=None,
                suite=parity.SUITE_COMPACTNESS, command=command,
                source_context=context, stdout_raw=raw, stderr_raw=empty)
            stale = dict(result, dependencyEpoch='tsj-generation-dependencies-v26')
            with self.assertRaises(ValueError):
                parity.validate_result(
                    stale, side_name=side_name, cohort_id=None,
                    suite=parity.SUITE_COMPACTNESS, command=command,
                    source_context=context, stdout_raw=raw, stderr_raw=empty)
            if side_name == 'candidate':
                stale_revision = dict(result, sourceSnapshotRevision=2)
                with self.assertRaises(ValueError):
                    parity.validate_result(
                        stale_revision, side_name=side_name, cohort_id=None,
                        suite=parity.SUITE_COMPACTNESS, command=command,
                        source_context=context, stdout_raw=raw, stderr_raw=empty)


if __name__ == '__main__':
    unittest.main()
