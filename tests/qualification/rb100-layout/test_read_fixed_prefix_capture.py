"""Small strict-reader contracts for fixed-prefix native capture packets."""

import copy
import hashlib
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import read_fixed_prefix_capture as capture


RUN_ID = '01234567-89ab-4cde-8fab-0123456789ab'


def _sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def _graph(variant):
    case_id = capture.VARIANTS[variant]['graphCaseId']
    cells = 6
    goals = [False] * (2 * cells)
    goals[5] = True
    goals[cells + 5] = True
    goals[4] = True
    goals[cells + 4] = True
    payload = {
        'protocol': capture.graph_oracle.GRAPH_PROTOCOL,
        'model': capture.graph_oracle.GRAPH_MODEL,
        'caseId': case_id,
        'width': 3,
        'height': 2,
        'primary': 1,
        'steps': [0] * (2 * cells),
        'vias': [False] * cells,
        'goals': goals,
        'starts': [0, cells],
    }
    body = json.dumps(payload, sort_keys=True, separators=(',', ':'),
                      ensure_ascii=True, allow_nan=False)
    raw, parsed, graph = capture.parse_graph_line(capture.GRAPH_TAG + body)
    return payload, raw, parsed, graph


def _layout_output():
    return {
        'layoutReturned': False,
        'zeroOutput': True,
        'fingerprint': None,
        'outline': None,
        'rawSegments': 0,
        'topTraceCount': 0,
        'bottomTraceCount': 0,
        'totalTraceCount': 0,
        'holeCount': 0,
        'viaCount': 0,
        'platedHoleCount': 0,
        'nonPlatedHoleCount': 0,
    }


def _operation(operation_id, variant):
    mode = 'NULL' if operation_id.endswith('_NULL') else 'CAPTURE'
    canonical = 'outcome=REJECTED'
    return {
        'protocolVersion': 1,
        'runId': RUN_ID,
        'operationId': operation_id,
        'variant': variant,
        'mode': mode,
        'status': 'PASS',
        'terminalStatus': 'EXHAUSTED',
        'policyOutcome': 'REJECTED',
        'selectedRoutePolicy': None,
        'policySelectedPlacementAttempt': -1,
        'firstP07RequestAttempt': 4,
        'routeEligiblePlacementAttempts': list(
            capture.VARIANTS[variant]['eligiblePlacementAttempts']),
        'routeEligibilitySkips': [],
        'routeOutcomes': ['P07_FULLER_TWO_LAYER@4=NO_PATH'],
        'primaryRoutingOrders': 1,
        'primaryRoutingExpansions': 1,
        'policyCanonicalLength': len(canonical),
        'policyCanonicalSha256': _sha(canonical),
        'policyCanonical': canonical,
        'layoutOutput': _layout_output(),
        'components': 100,
        'pads': 246,
        'nets': 80,
        'rootSeed': capture.ROOT_SEED,
        'placementSeed': capture.PLACEMENT_SEED,
        'routingSeed': capture.ROUTING_SEED,
        'layoutVersion': capture.LAYOUT_VERSION,
        'dependencyEpoch': capture.DEPENDENCY_EPOCH,
        'planVersion': capture.PLAN_VERSION,
        'modelVersion': capture.MODEL_VERSION,
        'physicalIntentVersion': capture.PHYSICAL_INTENT_VERSION,
        'operationMs': 10,
        'cleanupMs': 5,
        'cleanupStatus': 'PASS',
        'workUnits': 200,
        'maxUnitMs': 20,
        'failurePhase': '',
        'failureType': 'none',
        'cleanupFailureType': 'none',
        'scope': capture.OPERATION_SCOPE,
        'layoutQualification': False,
    }


def _fact(variant, operation, payload, graph, graph_raw):
    raw_placement = 'placement-lineage-fixture'
    prefix_geometry = 'prefix-geometry-fixture'
    return {
        'protocolVersion': 1,
        'runId': RUN_ID,
        'operationId': operation['operationId'],
        'mode': 'CAPTURE',
        'variant': variant,
        'rootSeed': capture.ROOT_SEED,
        'placementSeed': capture.PLACEMENT_SEED,
        'routingSeed': capture.ROUTING_SEED,
        'layoutVersion': capture.LAYOUT_VERSION,
        'dependencyEpoch': capture.DEPENDENCY_EPOCH,
        'planVersion': capture.PLAN_VERSION,
        'modelVersion': capture.MODEL_VERSION,
        'physicalIntentVersion': capture.PHYSICAL_INTENT_VERSION,
        'components': 100,
        'pads': 246,
        'nets': 80,
        'routeEligiblePlacementAttempts': list(
            capture.VARIANTS[variant]['eligiblePlacementAttempts']),
        'selectedPlacementIndex': 0,
        'selectedPlacementAttempt': 4,
        'routeRequestIndex': 0,
        'rawPlacementFingerprint': raw_placement,
        'rawPlacementFingerprintLength': len(raw_placement),
        'rawPlacementFingerprintSha256': _sha(raw_placement),
        'graphCaseId': graph.case_id,
        'graphSha256': graph.graph_sha256,
        'graphBytes': len(graph_raw),
        'gridWidth': 3,
        'gridHeight': 2,
        'gridOriginX': 100,
        'gridOriginY': 200,
        'gridPitch': 10,
        'cellCount': 6,
        'primaryLayer': 1,
        'startCell': 0,
        'initialFacesMask': 3,
        'pass': 0,
        'branchOrdinal': 1,
        'netId': 'net-root',
        'startPadId': 'pad-start',
        'rootPadId': 'pad-root',
        'startX': 100,
        'startY': 200,
        'rootX': 120,
        'rootY': 210,
        'reachedPadIds': ['pad-root', 'pad-terminal'],
        'reachedPthPadIds': ['pad-terminal'],
        'terminalReason': 'NO_PATH',
        'prefixGeometryFingerprint': prefix_geometry,
        'prefixGeometryFingerprintLength': len(prefix_geometry),
        'prefixGeometryFingerprintSha256': _sha(prefix_geometry),
        'prefixHoleCount': 0,
        'prefixViaCount': 0,
        'prefixHoleCountsByKind': {'VIA': 0, 'PLATED': 0, 'NON_PLATED': 0},
        'prefixHoles': [],
        'branchExpanded': 1,
        'totalRouteExpansions': 1,
        'goalsTop': 2,
        'goalsBottom': 2,
        'captureWorkUnits': 641,
        'copyEntries': 1,
        'goalProbes': 12,
        'ordinaryEdgeProbes': 28,
        'viaProbes': 6,
        'comparisonEntries': 1,
        'captureActiveElapsedMillis': 1,
        'serializationElapsedMillis': 1,
        'scope': capture.FACT_SCOPE,
        'reachabilityClaim': False,
        'layoutQualification': False,
    }


def _pair(variant):
    config = capture.VARIANTS[variant]
    return {
        'protocolVersion': 1,
        'runId': RUN_ID,
        'variant': variant,
        'nullOperationId': config['nullOperationId'],
        'captureOperationId': config['captureOperationId'],
        'eligiblePlacementAttemptsEqual': True,
        'firstP07RequestAttemptEqual': True,
        'routeOutcomesEqual': True,
        'primaryRoutingOrdersEqual': True,
        'primaryRoutingExpansionsEqual': True,
        'policyCanonicalAndTerminalEqual': True,
        'layoutOutputEqual': True,
        'status': 'PASS',
        'parityStatus': 'PASS',
        'scope': capture.PAIR_SCOPE,
        'layoutQualification': False,
    }


def _bundle():
    operations, facts, graphs, pairs = [], [], [], []
    for variant in capture.VARIANTS:
        config = capture.VARIANTS[variant]
        null_operation = _operation(config['nullOperationId'], variant)
        capture_operation = _operation(config['captureOperationId'], variant)
        payload, graph_raw, parsed_payload, graph = _graph(variant)
        operations.extend([null_operation, capture_operation])
        facts.append(_fact(variant, capture_operation, payload, graph, graph_raw))
        graphs.append({'bytes': graph_raw, 'payload': parsed_payload, 'graph': graph})
        pairs.append(_pair(variant))
    summary = {
        'status': 'PASS',
        'operations': 4,
        'nullOperations': 2,
        'captureOperations': 2,
        'pairs': 2,
        'graphs': 2,
        'authoredVariants': 2,
        'variants': list(capture.VARIANTS),
        'runId': RUN_ID,
        'rootSeed': capture.ROOT_SEED,
        'placementSeed': capture.PLACEMENT_SEED,
        'routingSeed': capture.ROUTING_SEED,
        'layoutVersion': capture.LAYOUT_VERSION,
        'dependencyEpoch': capture.DEPENDENCY_EPOCH,
        'planVersion': capture.PLAN_VERSION,
        'modelVersion': capture.MODEL_VERSION,
        'physicalIntentVersion': capture.PHYSICAL_INTENT_VERSION,
        'assertions': 100,
        'suiteMs': 10,
        'suiteLimitMs': 900_000,
        'scope': capture.OPERATION_SCOPE,
        'layoutQualification': False,
    }
    return operations, facts, graphs, pairs, summary


def _tag(prefix, value):
    return prefix + json.dumps(value, sort_keys=True, separators=(',', ':'))


def _receipt_lines():
    operations, facts, graphs, pairs, summary = _bundle()
    lines = []
    graph_by_case = {item['graph'].case_id: item for item in graphs}
    fact_by_variant = {item['variant']: item for item in facts}
    operation_by_id = {item['operationId']: item for item in operations}
    pair_by_variant = {item['variant']: item for item in pairs}
    for variant, config in capture.VARIANTS.items():
        lines.append(_tag(capture.OPERATION_TAG,
                          operation_by_id[config['nullOperationId']]))
        graph = graph_by_case[config['graphCaseId']]
        lines.append(capture.GRAPH_TAG + graph['bytes'].decode('ascii'))
        lines.append(_tag(capture.FACT_TAG, fact_by_variant[variant]))
        lines.append(_tag(capture.OPERATION_TAG,
                          operation_by_id[config['captureOperationId']]))
        lines.append(_tag(capture.PAIR_TAG, pair_by_variant[variant]))
    lines.append(capture.PASS_PREFIX + json.dumps(summary, sort_keys=True,
                                                 separators=(',', ':')))
    return lines


def _synthetic_native_files():
    source_java = set(capture.EXPECTED_PRODUCTION_INPUTS) | capture.RMO_PRODUCTION_INPUTS
    contract_java = {
        path for path in capture.REQUIRED_NATIVE_INPUTS
        if path.startswith('tests/contracts/') and path.endswith('.java')
    }
    index = 0
    while len(source_java) < capture.SOURCE_JAVA_COUNT:
        source_java.add('src/fixture/SyntheticSource{:04d}.java'.format(index))
        index += 1
    index = 0
    while len(contract_java) < capture.CONTRACT_JAVA_COUNT:
        contract_java.add('tests/contracts/SyntheticContract{:04d}.java'.format(index))
        index += 1
    paths = source_java | contract_java | capture.NATIVE_CENSUS_STATIC_INPUTS
    files = {
        relative: ('synthetic native source\n' + relative + '\n').encode('utf-8')
        for relative in paths
    }
    return files


def _write_native_input_spec(spec_dir, source_root, files, *, rows=None,
                             revision=None, input_count=None):
    spec_dir = Path(spec_dir)
    source_root = Path(source_root)
    for relative, raw in files.items():
        path = source_root.joinpath(*relative.split('/'))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    if rows is None:
        rows = [
            {'path': relative, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}
            for relative, raw in sorted(files.items())
        ]
    pin_raw = json.dumps(rows, sort_keys=True, separators=(',', ':'),
                         ensure_ascii=True).encode('utf-8')
    pin_path = spec_dir / 'source-pins.json'
    pin_path.write_bytes(pin_raw)
    for artifact_name in ('result.json', 'stdout.txt', 'receipt.txt', 'stderr.txt'):
        (spec_dir / artifact_name).write_bytes(b'')
    return {
        'schema': capture.INPUT_SCHEMA,
        'sourceRoot': str(source_root),
        'pinsPath': pin_path.name,
        'pinsSha256': hashlib.sha256(pin_raw).hexdigest(),
        'inputCount': len(rows) if input_count is None else input_count,
        'inputBytes': sum(row['bytes'] for row in rows),
        'sourceSnapshotRevision': (
            capture.SOURCE_REVISION if revision is None else revision),
        'resultPath': 'result.json',
        'stdoutPath': 'stdout.txt',
        'receiptPath': 'receipt.txt',
        'stderrPath': 'stderr.txt',
    }


class FixedPrefixCaptureReaderContracts(unittest.TestCase):
    def test_imported_graph_oracle_is_bound_to_reviewed_bytes(self):
        self.assertEqual(capture.validate_oracle_binding(), capture.ORACLE_SOURCE_SHA256)

    def test_current_dependency_epoch_rejects_stale_v26_packets(self):
        self.assertEqual(capture.DEPENDENCY_EPOCH, 'tsj-generation-dependencies-v27')
        self.assertEqual(capture.LAYOUT_VERSION, 25)
        self.assertEqual(capture.PLAN_VERSION, 1)
        self.assertEqual(capture.MODEL_VERSION, 4)
        self.assertEqual(capture.PHYSICAL_INTENT_VERSION, 9)
        stale_epoch = 'tsj-generation-dependencies-v26'

        operations, facts, graphs, pairs, summary = _bundle()
        bad_operations = copy.deepcopy(operations)
        bad_operations[0]['dependencyEpoch'] = stale_epoch
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(
                bad_operations, facts, graphs, pairs, summary)

        bad_facts = copy.deepcopy(facts)
        bad_facts[0]['dependencyEpoch'] = stale_epoch
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(
                operations, bad_facts, graphs, pairs, summary)

        bad_summary = dict(summary, dependencyEpoch=stale_epoch)
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(
                operations, facts, graphs, pairs, bad_summary)

    def test_closed_current_native_manifest_and_stale_snapshot_rejections(self):
        self.assertEqual(capture.SOURCE_REVISION, 3)
        self.assertEqual(capture.SOURCE_INPUT_COUNT, 1066)
        self.assertEqual(capture.SOURCE_JAVA_COUNT, 850)
        self.assertEqual(capture.CONTRACT_JAVA_COUNT, 211)
        self.assertEqual(capture.RMO_PRODUCTION_INPUTS, {
            'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrdering.java',
            'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingChecks.java',
            'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingRuntimeChecks.java',
        })
        self.assertEqual(capture.NATIVE_CENSUS_STATIC_INPUTS, {
            'scripts/verify-current-contracts.ps1',
            'scripts/VerifierIsolation.psm1',
            'tests/contracts/current-contract-runner-contract.ps1',
            '.tools/gwt-2.7.0/gwt-user-2.7.0.jar',
            '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar',
        })
        self.assertEqual(capture.REQUIRED_NATIVE_INPUTS,
                         set(capture.EXPECTED_PRODUCTION_INPUTS) |
                         capture.RMO_PRODUCTION_INPUTS |
                         capture.NATIVE_CENSUS_STATIC_INPUTS | {
                             'tests/contracts/Rb100PhysicalDiagnostics.java',
                             'tests/contracts/Rb100FixedPrefixCaptureContractTest.java',
                         })

        with tempfile.TemporaryDirectory() as temp:
            spec_dir = Path(temp)
            source_root = spec_dir / 'source'
            source_root.mkdir()
            files = _synthetic_native_files()
            spec = _write_native_input_spec(spec_dir, source_root, files)
            context = capture.validate_input_spec(spec, spec_dir)
            self.assertEqual(context['inputCount'], 1066)
            self.assertEqual(context['sourceSnapshotRevision'], 3)
            self.assertEqual(set(context['pins']), set(files))

            for stale in (
                    dict(spec, sourceSnapshotRevision=2),
                    dict(spec, inputCount=1063),
                    dict(spec, sourceSnapshotRevision=2, inputCount=1063)):
                with self.assertRaises(capture.CaptureInputError):
                    capture.validate_input_spec(stale, spec_dir)

            omitted = sorted(capture.RMO_PRODUCTION_INPUTS)[0]
            omitted_raw = files[omitted]
            omitted_path = source_root.joinpath(*omitted.split('/'))
            omitted_path.unlink()
            replacement = 'src/fixture/RmoReplacement.java'
            missing_files = dict(files)
            del missing_files[omitted]
            missing_files[replacement] = b'replacement source\n'
            missing_spec = _write_native_input_spec(
                spec_dir, source_root, missing_files)
            with self.assertRaises(capture.CaptureInputError):
                capture.validate_input_spec(missing_spec, spec_dir)
            source_root.joinpath(*replacement.split('/')).unlink()
            omitted_path.parent.mkdir(parents=True, exist_ok=True)
            omitted_path.write_bytes(omitted_raw)

            generic = next(path for path in files
                           if path.startswith('src/fixture/SyntheticSource'))
            unclosed_files = dict(files)
            del unclosed_files[generic]
            unclosed_files['outside-census/extra.txt'] = b'extra manifest path\n'
            unclosed_spec = _write_native_input_spec(
                spec_dir, source_root, unclosed_files)
            with self.assertRaises(capture.CaptureInputError):
                capture.validate_input_spec(unclosed_spec, spec_dir)

            rows = [
                {'path': relative, 'bytes': len(raw),
                 'sha256': hashlib.sha256(raw).hexdigest()}
                for relative, raw in sorted(files.items())
            ]
            rows[-1] = dict(rows[0])
            duplicate_spec = _write_native_input_spec(
                spec_dir, source_root, files, rows=rows)
            with self.assertRaises(capture.CaptureInputError):
                capture.validate_input_spec(duplicate_spec, spec_dir)

            valid_spec = _write_native_input_spec(spec_dir, source_root, files)
            selected = next(path for path in sorted(files)
                            if path.startswith('src/fixture/SyntheticSource'))
            selected_path = source_root.joinpath(*selected.split('/'))
            selected_raw = selected_path.read_bytes()
            selected_path.write_bytes(selected_raw + b'changed')
            with self.assertRaises(capture.CaptureInputError):
                capture.validate_input_spec(valid_spec, spec_dir)
            selected_path.write_bytes(selected_raw)

    def test_closed_native_manifest_rejects_reparse_source_entries(self):
        with tempfile.TemporaryDirectory() as temp:
            spec_dir = Path(temp)
            source_root = spec_dir / 'source'
            source_root.mkdir()
            files = _synthetic_native_files()
            spec = _write_native_input_spec(spec_dir, source_root, files)
            target = source_root / 'src/fixture/SyntheticSource0000.java'
            real_reparse_check = capture._is_reparse

            def mark_target_as_reparse(path):
                if Path(path).absolute() == target.absolute():
                    return True
                return real_reparse_check(path)

            with mock.patch.object(capture, '_is_reparse', side_effect=mark_target_as_reparse):
                with self.assertRaises(capture.CaptureInputError):
                    capture.validate_input_spec(spec, spec_dir)

    def test_new_output_leaf_is_allowed_but_missing_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            output_parent = base / 'output'
            output_parent.mkdir()
            output_path = output_parent / 'capture-result.json'
            self.assertFalse(output_path.exists())
            self.assertFalse(capture._is_reparse(output_path))
            self.assertFalse(capture._has_reparse_component(output_path))

            args = type('Args', (), {
                'spec': base / 'synthetic-spec.json',
                'spec_sha256': 'a' * 64,
                'output': output_path,
            })()
            stdout = io.StringIO()
            with mock.patch.object(capture, 'parse_args', return_value=args), \
                    mock.patch.object(capture, 'run_validation',
                                      return_value={'synthetic': True}), \
                    redirect_stdout(stdout):
                self.assertEqual(capture.main(), 0)
            self.assertEqual(output_path.read_text(encoding='utf-8'),
                             '{"synthetic":true}\n')
            self.assertIn('PASS: fixed-prefix capture analysis only', stdout.getvalue())

            missing_source = base / 'missing-source'
            with self.assertRaises(capture.CaptureInputError) as raised:
                capture.resolve_input(str(missing_source), base, 'MISSING_SOURCE')
            self.assertEqual(str(raised.exception), 'MISSING_SOURCE_PATH_UNAVAILABLE')

    def test_dangling_link_predicate_runs_before_missing_leaf_stat(self):
        dangling = Path('dangling-link-fixture')
        with mock.patch.object(Path, 'is_symlink', return_value=True), \
                mock.patch.object(Path, 'stat', side_effect=AssertionError('stat must not run')):
            self.assertTrue(capture._is_reparse(dangling))

    def test_existing_reparse_predicate_and_other_os_errors_fail_closed(self):
        fixture = Path('existing-reparse-fixture')
        with mock.patch.object(Path, 'is_symlink', return_value=False), \
                mock.patch.object(Path, 'is_junction', return_value=True):
            self.assertTrue(capture._is_reparse(fixture))

        with mock.patch.object(Path, 'is_symlink', return_value=False), \
                mock.patch.object(Path, 'is_junction', return_value=False), \
                mock.patch.object(Path, 'stat', side_effect=PermissionError('denied')):
            self.assertTrue(capture._is_reparse(fixture))

    def test_complete_synthetic_packet_preserves_narrow_scope(self):
        operations, facts, graphs, pairs, summary = _bundle()
        run_id, checked = capture.validate_operation_census(
            operations, facts, graphs, pairs, summary)
        self.assertEqual(run_id, RUN_ID)
        self.assertEqual(len(checked), 2)
        for row in checked:
            payload_bytes = json.dumps(row['graphPayload'], sort_keys=True,
                                       separators=(',', ':'), ensure_ascii=True).encode('ascii')
            self.assertIs(type(row['graphBytes']), int)
            self.assertEqual(row['graphBytes'], len(payload_bytes))
            self.assertFalse(row['oracle']['acceptance']['actualCaptureAttested'])
            self.assertFalse(row['oracle']['acceptance']['layout'])
            self.assertFalse(row['oracle']['acceptance']['electrical'])
            self.assertFalse(row['oracle']['acceptance']['q100'])

    def test_receipt_requires_one_exact_graph_and_fact_per_capture(self):
        lines = _receipt_lines()
        operations, facts, graphs, pairs, summary = capture._parse_events(lines)
        self.assertEqual((len(operations), len(facts), len(graphs), len(pairs)), (4, 2, 2, 2))
        missing_fact = list(lines)
        fact_index = next(i for i, line in enumerate(missing_fact)
                          if line.startswith(capture.FACT_TAG))
        del missing_fact[fact_index]
        with self.assertRaises(capture.CaptureInputError):
            capture._parse_events(missing_fact)
        duplicate_fact = list(lines)
        fact_index = next(i for i, line in enumerate(duplicate_fact)
                          if line.startswith(capture.FACT_TAG))
        duplicate_fact.insert(fact_index + 1, duplicate_fact[fact_index])
        with self.assertRaises(capture.CaptureInputError):
            capture._parse_events(duplicate_fact)
        duplicate_graph = list(lines)
        graph_index = next(i for i, line in enumerate(duplicate_graph)
                           if line.startswith(capture.GRAPH_TAG))
        duplicate_graph.insert(graph_index + 1, duplicate_graph[graph_index])
        with self.assertRaises(capture.CaptureInputError):
            capture._parse_events(duplicate_graph)

    def test_wrong_variant_seed_or_request_lineage_is_rejected(self):
        operations, facts, graphs, pairs, summary = _bundle()
        bad = copy.deepcopy(facts)
        bad[0]['variant'] = 'UNDECLARED_VARIANT'
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['rootSeed'] = 9007199254740993
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['routeRequestIndex'] = 1
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['nets'] -= 1
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['reachedPthPadIds'] = ['pad-root']
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

    def test_grid_skip_uses_canonical_eligibility_fact_and_disjoint_attempt(self):
        variant = 'SHARED_HYSTERETIC_LINEAR'
        operation = _operation('SHARED_NULL', variant)
        operation['routeEligibilitySkips'] = [
            'attempt=5;width=501;height=500;cells=250500;reason=GRID_LIMIT']
        capture._validate_operation(operation, 'SHARED_NULL', RUN_ID)
        for bad_skip in (
                'attempt=4;width=501;height=500;cells=250500;reason=GRID_LIMIT',
                'attempt=5;width=501;height=500;cells=250000;reason=GRID_LIMIT',
                'attempt=5;width=501;height=500;cells=250500;reason=NO_PATH'):
            bad = copy.deepcopy(operation)
            bad['routeEligibilitySkips'] = [bad_skip]
            with self.assertRaises(capture.CaptureInputError):
                capture._validate_operation(bad, 'SHARED_NULL', RUN_ID)

    def test_graph_bits_digest_and_capture_work_cap_are_bound(self):
        operations, facts, graphs, pairs, summary = _bundle()
        mutated_graphs = copy.deepcopy(graphs)
        payload = mutated_graphs[0]['payload']
        payload['goals'][1] = True
        graph_raw = json.dumps(payload, sort_keys=True, separators=(',', ':'),
                               ensure_ascii=True).encode('ascii')
        _, parsed, graph = capture.parse_graph_line(capture.GRAPH_TAG + graph_raw.decode('ascii'))
        mutated_graphs[0].update(bytes=graph_raw, payload=parsed, graph=graph)
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, facts, mutated_graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['ordinaryEdgeProbes'] = 14
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        bad = copy.deepcopy(facts)
        bad[0]['captureWorkUnits'] = capture.MAX_CAPTURE_WORK_UNITS + 1
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(operations, bad, graphs, pairs, summary)

        low_case_budget = copy.deepcopy(operations)
        low_case_budget[0]['workUnits'] = 160
        low_case_budget[1]['workUnits'] = 160
        with self.assertRaises(capture.CaptureInputError):
            capture.validate_operation_census(low_case_budget, facts, graphs, pairs, summary)

    def test_run_result_rejects_unproven_process_or_cleanup(self):
        stdout = b'captured native output\n'
        stderr = b''
        receipt = b'captured receipt\n'
        context = {
            'sourceSnapshotRevision': capture.SOURCE_REVISION,
            'inputCount': capture.SOURCE_INPUT_COUNT,
            'inputBytes': 1200,
            'pinsSha256': 'a' * 64,
        }
        result = {
            'schema': capture.RUN_SCHEMA,
            'source': 'candidate',
            'suite': capture.SUITE,
            'cohortId': None,
            'command': capture.expected_command(),
            'layoutVersion': capture.LAYOUT_VERSION,
            'dependencyEpoch': capture.DEPENDENCY_EPOCH,
            'sourceSnapshotRevision': capture.SOURCE_REVISION,
            'inputCount': capture.SOURCE_INPUT_COUNT,
            'inputBytes': 1200,
            'inputPinsSha256': 'a' * 64,
            'inputAudit': 'PASS_BEFORE_AND_AFTER',
            'exitCode': 0,
            'nativeExitCode': 0,
            'terminationProven': True,
            'cleanupStatus': 'PASS',
            'cleanupElapsedMs': None,
            'nativeLimitMs': 900000,
            'outerLimitMs': capture.MAX_OUTER_MS,
            'processId': 1234,
            'processStartTicks': '987654321',
            'elapsedSeconds': 2.5,
            'requestedExecutable': 'powershell.exe',
            'stdoutBytes': len(stdout),
            'stdoutSha256': capture.sha_bytes(stdout),
            'stderrBytes': 0,
            'stderrSha256': capture.sha_bytes(stderr),
            'receiptBytes': len(receipt),
            'receiptSha256': capture.sha_bytes(receipt),
        }
        capture.validate_run_result(result, context, stdout, receipt, stderr)
        for bad in (dict(result, processId='1234'),
                    dict(result, cleanupStatus='FAIL'),
                    dict(result, terminationProven=False),
                    dict(result, sourceSnapshotRevision=2),
                    dict(result, inputCount=1063),
                    dict(result, dependencyEpoch='tsj-generation-dependencies-v26')):
            with self.assertRaises(capture.CaptureInputError):
                capture.validate_run_result(bad, context, stdout, receipt, stderr)

    def test_duplicate_json_keys_and_noncanonical_graph_are_rejected(self):
        with self.assertRaises(capture.CaptureInputError):
            capture.strict_json('{"x":1,"x":2}')
        payload, _, _, _ = _graph('SHARED_HYSTERETIC_LINEAR')
        spaced = json.dumps(payload, sort_keys=True, separators=(', ', ': '))
        with self.assertRaises(capture.CaptureInputError):
            capture.parse_graph_line(capture.GRAPH_TAG + spaced)

    def test_layout_output_uses_emitted_java_hex_hash_and_exact_zero_shape(self):
        zero = _layout_output()
        capture._validate_layout_output(zero, 'fixture')
        fingerprint = 'geometry-fixture'
        returned = {
            'layoutReturned': True,
            'zeroOutput': False,
            'fingerprint': fingerprint,
            'fingerprintLength': len(fingerprint),
            'fingerprintJavaHash': capture._java_string_hash_hex(fingerprint),
            'outline': {'x': 0, 'y': 0, 'width': 20, 'height': 10},
            'rawSegments': 1,
            'topTraceCount': 1,
            'bottomTraceCount': 0,
            'totalTraceCount': 1,
            'holeCount': 0,
            'viaCount': 0,
            'platedHoleCount': 0,
            'nonPlatedHoleCount': 0,
        }
        capture._validate_layout_output(returned, 'fixture')
        bad = dict(returned, fingerprintJavaHash='0')
        with self.assertRaises(capture.CaptureInputError):
            capture._validate_layout_output(bad, 'fixture')
        bad = dict(zero, fingerprintLength=None)
        with self.assertRaises(capture.CaptureInputError):
            capture._validate_layout_output(bad, 'fixture')

    def test_operation_result_distinguishes_winner_from_exhausted_policy(self):
        exhausted = _operation('SHARED_NULL', 'SHARED_HYSTERETIC_LINEAR')
        capture._validate_operation(exhausted, 'SHARED_NULL', RUN_ID)
        bad_exhausted = dict(exhausted, selectedRoutePolicy='P07_FULLER_TWO_LAYER')
        with self.assertRaises(capture.CaptureInputError):
            capture._validate_operation(bad_exhausted, 'SHARED_NULL', RUN_ID)

        fingerprint = 'geometry-fixture'
        routed = _operation('SHARED_NULL', 'SHARED_HYSTERETIC_LINEAR')
        routed.update({
            'terminalStatus': 'ROUTED',
            'policyOutcome': 'SUCCESS',
            'selectedRoutePolicy': 'P07_FULLER_TWO_LAYER',
            'policySelectedPlacementAttempt': 4,
            'routeOutcomes': ['P07_FULLER_TWO_LAYER@4=SUCCESS'],
            'policyCanonical': 'outcome=SUCCESS',
        })
        routed['policyCanonicalLength'] = len(routed['policyCanonical'])
        routed['policyCanonicalSha256'] = _sha(routed['policyCanonical'])
        routed['layoutOutput'] = {
            'layoutReturned': True,
            'zeroOutput': False,
            'fingerprint': fingerprint,
            'fingerprintLength': len(fingerprint),
            'fingerprintJavaHash': capture._java_string_hash_hex(fingerprint),
            'outline': {'x': 0, 'y': 0, 'width': 20, 'height': 10},
            'rawSegments': 1,
            'topTraceCount': 1,
            'bottomTraceCount': 0,
            'totalTraceCount': 1,
            'holeCount': 0,
            'viaCount': 0,
            'platedHoleCount': 0,
            'nonPlatedHoleCount': 0,
        }
        capture._validate_operation(routed, 'SHARED_NULL', RUN_ID)
        bad_routed = copy.deepcopy(routed)
        bad_routed['layoutOutput'] = _layout_output()
        with self.assertRaises(capture.CaptureInputError):
            capture._validate_operation(bad_routed, 'SHARED_NULL', RUN_ID)


if __name__ == '__main__':
    unittest.main()
