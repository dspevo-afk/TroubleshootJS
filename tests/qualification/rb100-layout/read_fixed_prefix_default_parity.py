#!/usr/bin/env python3
"""Strictly compare accepted layout25 default outputs with a fixed-prefix candidate.

This reader is a source-bound parity check only. It reuses the maintained saved
profile catalog, case validators, and independent physical oracles, but it does not
change or retarget the historical layout-transition readers.
"""
import argparse
import ast
import copy
import hashlib
import json
import re
from pathlib import Path

import read_diagnostic as diagnostic
from read_diagnostic import strict_json, need, integer
import read_saved_profile_transition as saved


SCHEMA = 'p07-fixed-prefix-default-parity-v1'
OUTPUT_PROTOCOL = 'p07-fixed-prefix-default-parity-result-v1'
ACCEPTED_BASELINE_COMMIT = '9a6736e12487d8f6e7ea15ccfecb8ecaa0c1c295'
SAVED_PROFILE_HELPER_SHA256 = '390acf30090bd5a3e29b64dcfc212d30fc26c6d1f590d49fd72ebb4d9aa51670'
DIAGNOSTIC_HELPER_SHA256 = '31f65be5e11472c7b09e8141292402c641df6b735787d32eb4c904ae098981e7'
LAYOUT_VERSION = 25
DEPENDENCY_EPOCH = 'tsj-generation-dependencies-v27'
SUITE_GEOMETRY = 'P07GeometryEpochDiagnosticContractTest'
SUITE_COMPACTNESS = 'PcbCompactnessContractTest'
ENTRY_SCRIPT = 'scripts/verify-current-contracts.ps1'
FOCUSED_PASS = 'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP_LINE = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
COMPACTNESS_LEGACY_PASS = (
    'PASS: current layout25 RB56 legacy-v1 geometry matches Q3 references; '
    'layout23 hashes retained separately')
COMPACTNESS_PASS_PREFIX = 'PASS: actual RB56 geometry report '
COHORT_IDS = [item['id'] for item in saved.COHORTS]
SOURCE_SNAPSHOT_REVISIONS = {'baseline': 1, 'candidate': 3}
COMPACTNESS_SEEDS = ['-1017', '-1004', '-1022']
EXPECTED_PRODUCTION_CHANGES = sorted([
    'src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java',
    'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',
    'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
])
EXPECTED_CHANGED_INPUTS = sorted(EXPECTED_PRODUCTION_CHANGES + [
    'scripts/verify-current-contracts.ps1',
    'tests/contracts/Rb100PhysicalDiagnostics.java',
])
EXPECTED_ADDED_INPUTS = sorted([
    'tests/contracts/P07FixedPrefixObservationContractTest.java',
    'tests/contracts/Rb100FixedPrefixCaptureContractTest.java',
])
UNCHANGED_ORACLES = sorted([
    'tests/contracts/PcbCompactnessContractTest.java',
    'tests/contracts/P07GeometryEpochDiagnosticContractTest.java',
    'tests/contracts/PcbFunctionalGroupingContractTest.java',
    'tests/contracts/PcbPocketPackingContractTest.java',
    'tests/contracts/P08ReferenceConductorBuilder.java',
    'tests/contracts/P08ScalabilityContractTest.java',
    'tests/contracts/P07LayeredRasterOracle.java',
    'src/com/lushprojects/circuitjs1/client/PcbTraceRules.java',
    'src/com/lushprojects/circuitjs1/client/Rb56Placement.java',
])
MAX_SPEC_BYTES = 4 * 1024 * 1024
MAX_PIN_BYTES = 16 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_RESULT_BYTES = 1024 * 1024
MAX_SOURCE_INPUTS = 10000
MAX_SOURCE_BYTES = 1024 * 1024 * 1024
VOLATILE_CASE_FIELDS = {'runId', 'frameId', 'nativeDiagnosticElapsedMs'}
VOLATILE_FRAME_FIELDS = {'runId'}
VOLATILE_SUMMARY_FIELDS = {'runId', 'nativeDiagnosticElapsedMs'}
_MISSING = object()
CURRENT_SAVED_METADATA_FUNCTIONS = ('validate_case', 'extract_native')
SAVED_DEPENDENCY_ASSIGNMENT = (
    "dependency = 'tsj-generation-dependencies-v' + str(layout_version + 1)")


def sha_bytes(raw):
    return hashlib.sha256(raw).hexdigest()


def validate_helper_sources():
    """Bind the imported validators to the reviewed pure-reader source bytes."""
    module_paths = {
        'read_saved_profile_transition.py': (saved, SAVED_PROFILE_HELPER_SHA256),
        'read_diagnostic.py': (diagnostic, DIAGNOSTIC_HELPER_SHA256),
    }
    reader_dir = Path(__file__).resolve(strict=True).parent
    digests = {}
    reader_path = Path(__file__).resolve(strict=True)
    reader_raw = read_bounded(reader_path, MAX_SPEC_BYTES, 'fixed-prefix parity reader source')
    digests['read_fixed_prefix_default_parity.py'] = sha_bytes(reader_raw)
    for name, (module, expected_digest) in module_paths.items():
        module_path = Path(module.__file__)
        try:
            resolved = module_path.resolve(strict=True)
        except (OSError, RuntimeError):
            raise ValueError('required independent reader source is absent: ' + name)
        need(resolved.parent == reader_dir and resolved.name == name and
             not module_path.is_symlink() and resolved.is_file(),
             'required independent reader source resolved outside the expected directory: ' + name)
        raw = read_bounded(resolved, 16 * 1024 * 1024, 'independent reader source')
        digest = sha_bytes(raw)
        need(digest == expected_digest,
             'required independent reader source differs from its reviewed bytes: ' + name)
        digests[name] = digest
    return digests


def _current_saved_profile_dependency(layout_version):
    need(type(layout_version) is int and layout_version == 25 and
         LAYOUT_VERSION == 25 and
         DEPENDENCY_EPOCH == 'tsj-generation-dependencies-v27',
         'current saved-profile metadata adapter only supports layout25/dependency-v27')
    return DEPENDENCY_EPOCH


def _assignment_targets_name(node, name):
    if isinstance(node, ast.Assign):
        targets = node.targets
    elif isinstance(node, (ast.AnnAssign, ast.AugAssign)):
        targets = [node.target]
    else:
        return False

    def contains(target):
        if isinstance(target, ast.Name):
            return target.id == name
        if isinstance(target, (ast.Tuple, ast.List)):
            return any(contains(item) for item in target.elts)
        return False

    return any(contains(target) for target in targets)


def _patch_saved_metadata_assignment(function_node):
    need(type(function_node) is ast.FunctionDef and
         function_node.name in CURRENT_SAVED_METADATA_FUNCTIONS,
         'saved-profile metadata adapter found an unexpected function')
    assignments = [node for node in ast.walk(function_node)
                   if _assignment_targets_name(node, 'dependency')]
    expected = ast.parse(SAVED_DEPENDENCY_ASSIGNMENT).body[0]
    need(len(assignments) == 1 and isinstance(assignments[0], ast.Assign) and
         ast.dump(assignments[0], include_attributes=False) ==
         ast.dump(expected, include_attributes=False),
         'saved-profile dependency assignment differs from its reviewed form')
    target = assignments[0]
    original_dump = ast.dump(function_node, include_attributes=False)
    original_value = copy.deepcopy(target.value)
    replacement = ast.parse(
        '_current_saved_profile_dependency(layout_version)', mode='eval').body
    target.value = ast.copy_location(replacement, target.value)
    ast.fix_missing_locations(function_node)

    restored = copy.deepcopy(function_node)
    restored_assignments = [node for node in ast.walk(restored)
                            if _assignment_targets_name(node, 'dependency')]
    need(len(restored_assignments) == 1, 'saved-profile adapter patch count changed')
    restored_assignments[0].value = original_value
    need(ast.dump(restored, include_attributes=False) == original_dump,
         'saved-profile adapter changed nodes beyond the metadata expression')
    return function_node


def _compile_saved_profile_metadata_adapters(source_raw, source_path):
    need(type(source_raw) is bytes and
         sha_bytes(source_raw) == SAVED_PROFILE_HELPER_SHA256,
         'saved-profile adapter source hash differs from reviewed bytes')
    try:
        tree = ast.parse(source_raw.decode('utf-8'), filename=str(source_path))
    except (SyntaxError, UnicodeError, ValueError):
        raise ValueError('saved-profile adapter source cannot be parsed') from None

    found = {name: [] for name in CURRENT_SAVED_METADATA_FUNCTIONS}
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name in found:
            found[node.name].append(node)
    need(all(len(found[name]) == 1 and type(found[name][0]) is ast.FunctionDef
             for name in CURRENT_SAVED_METADATA_FUNCTIONS),
         'saved-profile adapter function inventory differs from reviewed source')

    functions = []
    for name in CURRENT_SAVED_METADATA_FUNCTIONS:
        functions.append(_patch_saved_metadata_assignment(
            copy.deepcopy(found[name][0])))
    module = ast.Module(body=functions, type_ignores=[])
    ast.fix_missing_locations(module)
    isolated_globals = dict(saved.__dict__)
    need(isolated_globals is not saved.__dict__,
         'saved-profile adapter namespace is not isolated')
    isolated_globals['_current_saved_profile_dependency'] = (
        _current_saved_profile_dependency)
    try:
        code = compile(module, str(source_path), 'exec')
        exec(code, isolated_globals, isolated_globals)
    except (SyntaxError, TypeError, ValueError):
        raise ValueError('saved-profile adapter compilation failed') from None
    adapters = {name: isolated_globals[name]
                for name in CURRENT_SAVED_METADATA_FUNCTIONS}
    need(all(callable(adapters[name]) and
             adapters[name].__globals__ is isolated_globals
             for name in CURRENT_SAVED_METADATA_FUNCTIONS),
         'saved-profile adapter did not retain its isolated namespace')
    proof = {
        'sourceSha256': sha_bytes(source_raw),
        'functionNames': list(CURRENT_SAVED_METADATA_FUNCTIONS),
        'dependencyAssignmentsPatched': len(CURRENT_SAVED_METADATA_FUNCTIONS),
        'onlyMetadataExpressionsChanged': True,
        'globalsIsolated': True,
    }
    return adapters, proof


def current_saved_profile_metadata_adapters(helper_digests):
    module_path = Path(saved.__file__)
    reader_dir = Path(__file__).resolve(strict=True).parent
    try:
        resolved = module_path.resolve(strict=True)
    except (OSError, RuntimeError):
        raise ValueError('required saved-profile reader source is absent') from None
    need(resolved.parent == reader_dir and
         resolved.name == 'read_saved_profile_transition.py' and
         not module_path.is_symlink() and resolved.is_file(),
         'required saved-profile reader source resolved outside the expected directory')
    source_raw = read_bounded(resolved, 16 * 1024 * 1024,
                              'saved-profile metadata adapter source')
    source_digest = sha_bytes(source_raw)
    need(source_digest == SAVED_PROFILE_HELPER_SHA256 and
         helper_digests.get('read_saved_profile_transition.py') == source_digest,
         'saved-profile adapter source differs from its reviewed bytes')
    adapters, proof = _compile_saved_profile_metadata_adapters(source_raw, resolved)
    need(proof == {
        'sourceSha256': SAVED_PROFILE_HELPER_SHA256,
        'functionNames': list(CURRENT_SAVED_METADATA_FUNCTIONS),
        'dependencyAssignmentsPatched': 2,
        'onlyMetadataExpressionsChanged': True,
        'globalsIsolated': True,
    }, 'saved-profile metadata adapter proof differs')
    return adapters, proof


def exact_keys(value, keys, label):
    need(type(value) is dict and set(value) == set(keys), label + ' fields differ')


def safe_relative_path(value, label):
    need(type(value) is str and 0 < len(value) <= 4096,
         label + ' is not a bounded path string')
    normalized = value.replace('\\', '/')
    need(normalized == value and not normalized.startswith('/') and
         re.match(r'^[A-Za-z]:', normalized) is None and
         all(part not in ('', '.', '..') for part in normalized.split('/')),
         label + ' is not a canonical relative path')
    return normalized


def resolve_input(value, base, label):
    need(type(value) is str and 0 < len(value) <= 4096,
         label + ' path is invalid')
    path = Path(value)
    if not path.is_absolute():
        path = base / path
    try:
        return path.resolve(strict=True)
    except (OSError, RuntimeError):
        raise ValueError(label + ' path is absent or unresolved')


def read_bounded(path, limit, label):
    path = Path(path)
    try:
        if not path.is_file() or path.is_symlink():
            raise ValueError(label + ' is not a regular file')
        size = path.stat().st_size
        need(0 < size <= limit, label + ' exceeds its input bound')
        return path.read_bytes()
    except OSError:
        raise ValueError(label + ' could not be read')


def read_optional_empty(path, limit, label):
    path = Path(path)
    try:
        if not path.is_file() or path.is_symlink():
            raise ValueError(label + ' is not a regular file')
        size = path.stat().st_size
        need(0 <= size <= limit, label + ' exceeds its input bound')
        return path.read_bytes()
    except OSError:
        raise ValueError(label + ' could not be read')


def _has_reparse_component(root, path):
    try:
        relative = path.relative_to(root)
    except ValueError:
        return True
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink():
            return True
        is_junction = getattr(current, 'is_junction', None)
        if callable(is_junction) and is_junction():
            return True
    return False


def load_pin_snapshot(side, spec_dir, side_name):
    required = {
        'sourceRoot', 'pinsPath', 'pinsSha256', 'inputCount', 'inputBytes',
        'sourceSnapshotRevision', 'cohorts', 'compactnessRun',
    }
    exact_keys(side, required, side_name + ' source')
    root = resolve_input(side['sourceRoot'], spec_dir, side_name + ' source root')
    need(root.is_dir() and not root.is_symlink(), side_name + ' source root is not a directory')
    pin_path = resolve_input(side['pinsPath'], spec_dir, side_name + ' source pins')
    pin_raw = read_bounded(pin_path, MAX_PIN_BYTES, side_name + ' source pins')
    need(type(side['pinsSha256']) is str and
         re.fullmatch(r'[0-9a-f]{64}', side['pinsSha256']) is not None and
         sha_bytes(pin_raw) == side['pinsSha256'], side_name + ' source pin digest differs')
    rows = strict_json(pin_raw.decode('utf-8-sig'))
    need(type(rows) is list and 0 < len(rows) <= MAX_SOURCE_INPUTS,
         side_name + ' source pin inventory has invalid size')
    need(integer(side['inputCount'], 1, MAX_SOURCE_INPUTS) and
         side['inputCount'] == len(rows) and
         integer(side['inputBytes'], 1, MAX_SOURCE_BYTES) and
         side_name in SOURCE_SNAPSHOT_REVISIONS and
         integer(side['sourceSnapshotRevision'],
                 SOURCE_SNAPSHOT_REVISIONS[side_name],
                 SOURCE_SNAPSHOT_REVISIONS[side_name]),
         side_name + ' source context counts/revision are invalid')
    pin_map = {}
    pin_bytes = {}
    total_bytes = 0
    for row in rows:
        exact_keys(row, {'path', 'bytes', 'sha256'}, side_name + ' pin row')
        relative = safe_relative_path(row.get('path'), side_name + ' pinned input')
        need(relative not in pin_map and integer(row.get('bytes'), 0, MAX_SOURCE_BYTES) and
             type(row.get('sha256')) is str and
             re.fullmatch(r'[0-9a-f]{64}', row['sha256']) is not None,
             side_name + ' pin record is duplicate or invalid')
        file_path = root.joinpath(*relative.split('/'))
        try:
            resolved = file_path.resolve(strict=True)
        except (OSError, RuntimeError):
            raise ValueError(side_name + ' pinned source input is absent')
        need(not _has_reparse_component(root, file_path) and
             resolved.is_file(), side_name + ' pinned source input is not a contained regular file')
        try:
            actual_bytes = resolved.stat().st_size
            actual_hash = sha_bytes(resolved.read_bytes())
        except OSError:
            raise ValueError(side_name + ' pinned source input could not be read')
        need(actual_bytes == row['bytes'] and actual_hash == row['sha256'],
             side_name + ' pinned source input changed')
        total_bytes += actual_bytes
        need(total_bytes <= MAX_SOURCE_BYTES, side_name + ' source bytes exceed the bound')
        pin_map[relative] = row['sha256']
        pin_bytes[relative] = row['bytes']
    need(total_bytes == side['inputBytes'], side_name + ' source byte census differs')
    need(all(path in pin_map for path in UNCHANGED_ORACLES),
         side_name + ' source omits a required independent oracle')
    return {
        'root': root, 'pinPath': pin_path, 'pinRaw': pin_raw,
        'pinMap': pin_map, 'pinBytes': pin_bytes,
        'inputCount': len(rows), 'inputBytes': total_bytes,
        'pinsSha256': sha_bytes(pin_raw),
        'sourceSnapshotRevision': side['sourceSnapshotRevision'],
    }


def _read_pinned_source(context, relative, side_name):
    """Re-read one already-pinned file and ensure it still matches its raw pin."""
    need(relative in context['pinMap'] and relative in context['pinBytes'],
         side_name + ' source context omits a changed input')
    path = context['root'].joinpath(*relative.split('/'))
    try:
        resolved = path.resolve(strict=True)
        need(not _has_reparse_component(context['root'], path) and resolved.is_file(),
             side_name + ' changed source input is not a contained regular file')
        size = resolved.stat().st_size
        need(0 <= size <= MAX_SOURCE_BYTES, side_name + ' changed source input exceeds the bound')
        raw = resolved.read_bytes()
    except (OSError, RuntimeError):
        raise ValueError(side_name + ' changed source input could not be read')
    need(len(raw) == context['pinBytes'][relative] and
         sha_bytes(raw) == context['pinMap'][relative],
         side_name + ' changed source input no longer matches its raw pin')
    return raw


def _newline_normalized(raw):
    """Map CRLF to LF; return None if the bytes contain any lone CR."""
    normalized = raw.replace(b'\r\n', b'\n')
    if b'\r' in normalized:
        return None
    return normalized


def _newline_counts(raw):
    crlf = raw.count(b'\r\n')
    return crlf, raw.count(b'\n') - crlf


def validate_source_delta(baseline_context, candidate_context, expected_delta):
    exact_keys(expected_delta, {'changed', 'added', 'removed'}, 'source delta')
    baseline_map = baseline_context['pinMap']
    candidate_map = candidate_context['pinMap']
    common = set(baseline_map) & set(candidate_map)
    raw_changed = sorted(path for path in common
                         if baseline_map[path] != candidate_map[path] or
                         baseline_context['pinBytes'][path] != candidate_context['pinBytes'][path])
    newline_only = []
    semantic_changed = []
    for path in raw_changed:
        baseline_raw = _read_pinned_source(baseline_context, path, 'baseline')
        candidate_raw = _read_pinned_source(candidate_context, path, 'candidate')
        baseline_normalized = _newline_normalized(baseline_raw)
        candidate_normalized = _newline_normalized(candidate_raw)
        if baseline_normalized is not None and candidate_normalized is not None and \
                baseline_normalized == candidate_normalized:
            baseline_crlf, baseline_lf = _newline_counts(baseline_raw)
            candidate_crlf, candidate_lf = _newline_counts(candidate_raw)
            newline_only.append({
                'path': path,
                'baselineBytes': len(baseline_raw),
                'baselineSha256': baseline_map[path],
                'baselineCrlfCount': baseline_crlf,
                'baselineLfCount': baseline_lf,
                'candidateBytes': len(candidate_raw),
                'candidateSha256': candidate_map[path],
                'candidateCrlfCount': candidate_crlf,
                'candidateLfCount': candidate_lf,
                'commonLfSha256': sha_bytes(baseline_normalized),
            })
        else:
            semantic_changed.append(path)
    actual = {
        'changed': semantic_changed,
        'added': sorted(set(candidate_map) - set(baseline_map)),
        'removed': sorted(set(baseline_map) - set(candidate_map)),
    }
    for key in ('changed', 'added', 'removed'):
        expected = expected_delta[key]
        need(type(expected) is list and expected == sorted(set(expected)) and
             all(safe_relative_path(path, 'expected source delta path') == path for path in expected),
             'expected source delta is not canonical')
        need(actual[key] == expected, 'actual native input ' + key + ' paths differ from the frozen delta')
    need(actual == {
        'changed': EXPECTED_CHANGED_INPUTS,
        'added': EXPECTED_ADDED_INPUTS,
        'removed': [],
    }, 'native input delta differs from the exact frozen fixed-prefix scope')
    changed_production = sorted(path for path in actual['changed'] if path.startswith('src/'))
    need(changed_production == EXPECTED_PRODUCTION_CHANGES,
         'production source delta differs from the three authorized routing files')
    for path in UNCHANGED_ORACLES:
        need(path not in actual['changed'] and path in baseline_map and path in candidate_map,
             'default-parity oracle changed between the accepted source and candidate')
    return {
        **actual,
        'rawChanged': raw_changed,
        'lineEndingOnly': newline_only,
    }


def expected_command(suite, cohort_id=None):
    env = {} if cohort_id is None else {'TSJ_GEOMETRY_COHORT': cohort_id}
    return {
        'entry': ENTRY_SCRIPT,
        'suite': suite,
        'args': [],
        'environment': env,
    }


def validate_command(command, suite, cohort_id, label):
    exact_keys(command, {'entry', 'suite', 'args', 'environment'}, label + ' command')
    need(command == expected_command(suite, cohort_id), label + ' command identity differs')


def validate_result(result, *, side_name, cohort_id, suite, command,
                    source_context, stdout_raw, stderr_raw):
    required = {
        'schema', 'source', 'suite', 'cohortId', 'command', 'layoutVersion',
        'dependencyEpoch', 'sourceSnapshotRevision', 'inputCount', 'inputBytes',
        'inputPinsSha256', 'inputAudit', 'exitCode', 'nativeExitCode',
        'terminationProven', 'cleanupStatus', 'cleanupElapsedMs', 'nativeLimitMs',
        'outerLimitMs', 'processId', 'processStartTicks', 'elapsedSeconds',
        'requestedExecutable', 'stdoutBytes', 'stdoutSha256', 'stderrBytes',
        'stderrSha256', 'receiptBytes', 'receiptSha256',
    }
    need(type(result) is dict and required.issubset(result),
         side_name + ' native result omits required command/source/process evidence')
    need(result.get('schema') == 'p07-fixed-prefix-native-run-v1' and
         result.get('source') == side_name and result.get('suite') == suite and
         result.get('cohortId') == cohort_id and result.get('command') == command,
         side_name + ' actual native command identity differs from the frozen request')
    expected_source_revision = SOURCE_SNAPSHOT_REVISIONS.get(side_name)
    need(expected_source_revision is not None and
         result.get('layoutVersion') == LAYOUT_VERSION and
         result.get('dependencyEpoch') == DEPENDENCY_EPOCH and
         integer(result.get('sourceSnapshotRevision'),
                 expected_source_revision, expected_source_revision) and
         result.get('sourceSnapshotRevision') == source_context['sourceSnapshotRevision'] and
         result.get('inputCount') == source_context['inputCount'] and
         type(result.get('inputBytes')) in (int, float) and
         result.get('inputBytes') == source_context['inputBytes'] and
         result.get('inputPinsSha256') == source_context['pinsSha256'] and
         result.get('inputAudit') == 'PASS_BEFORE_AND_AFTER',
         side_name + ' native input/epoch binding differs from its sealed source')
    need(integer(result.get('exitCode'), 0, 0) and
         integer(result.get('nativeExitCode'), 0, 0) and
         result.get('terminationProven') is True and
         result.get('cleanupStatus') == 'PASS' and
         result.get('cleanupElapsedMs') is None,
         side_name + ' native exit, termination, or cleanup proof did not pass')
    expected_native_limit = 180000 if suite == SUITE_GEOMETRY else 60000
    need(result.get('nativeLimitMs') == expected_native_limit and
         result.get('outerLimitMs') == 1800000 and
         result.get('requestedExecutable') == 'powershell.exe',
         side_name + ' native/outer budgets differ from the maintained contract')
    need(integer(result.get('processId'), 1, (1 << 63) - 1) and
         type(result.get('processStartTicks')) is str and
         re.fullmatch(r'[1-9][0-9]{0,19}', result['processStartTicks']) and
         type(result.get('elapsedSeconds')) in (int, float) and
         0 < result['elapsedSeconds'] <= 1800,
         side_name + ' native process/elapsed evidence is invalid')
    need(integer(result.get('stdoutBytes'), 0, MAX_CAPTURE_BYTES) and
         result.get('stdoutBytes') == len(stdout_raw) and
         result.get('stdoutSha256') == sha_bytes(stdout_raw) and
         integer(result.get('stderrBytes'), 0, MAX_CAPTURE_BYTES) and
         result.get('stderrBytes') == len(stderr_raw) and
         result.get('stderrSha256') == sha_bytes(stderr_raw),
         side_name + ' native stdout/stderr bytes or digests differ')


def _read_run(run, spec_dir, side_name, source_context, cohort_id, suite):
    required = {'resultPath', 'stdoutPath', 'receiptPath', 'stderrPath', 'command'}
    exact_keys(run, required | ({'cohortId'} if suite == SUITE_GEOMETRY else set()),
               side_name + ' ' + suite + ' run')
    if suite == SUITE_GEOMETRY:
        need(run.get('cohortId') == cohort_id, side_name + ' cohort run order differs')
    validate_command(run['command'], suite, cohort_id, side_name)
    paths = {
        'result': resolve_input(run['resultPath'], spec_dir, side_name + ' run result'),
        'stdout': resolve_input(run['stdoutPath'], spec_dir, side_name + ' run stdout'),
        'receipt': resolve_input(run['receiptPath'], spec_dir, side_name + ' run receipt'),
        'stderr': resolve_input(run['stderrPath'], spec_dir, side_name + ' run stderr'),
    }
    result_raw = read_bounded(paths['result'], MAX_RESULT_BYTES, side_name + ' run result')
    stdout_raw = read_bounded(paths['stdout'], MAX_CAPTURE_BYTES, side_name + ' run stdout')
    receipt_raw = read_bounded(paths['receipt'], MAX_CAPTURE_BYTES, side_name + ' run receipt')
    stderr_raw = read_optional_empty(paths['stderr'], MAX_CAPTURE_BYTES, side_name + ' run stderr')
    result = strict_json(result_raw.decode('utf-8-sig'))
    validate_result(result, side_name=side_name, cohort_id=cohort_id, suite=suite,
                    command=run['command'], source_context=source_context,
                    stdout_raw=stdout_raw, stderr_raw=stderr_raw)
    need(integer(result.get('receiptBytes'), 0, MAX_CAPTURE_BYTES) and
         result.get('receiptBytes') == len(receipt_raw) and
         result.get('receiptSha256') == sha_bytes(receipt_raw),
         side_name + ' native receipt bytes or digest differ')
    return {
        'paths': paths, 'result': result, 'resultSha256': sha_bytes(result_raw),
        'stdoutRaw': stdout_raw, 'receiptRaw': receipt_raw, 'stderrRaw': stderr_raw,
        'stdoutSha256': sha_bytes(stdout_raw), 'receiptSha256': sha_bytes(receipt_raw),
        'stderrSha256': sha_bytes(stderr_raw),
    }


def _validate_cohort_run(run, cohort, spec_dir, side_name, source_context,
                         current_saved_adapters):
    artifacts = _read_run(run, spec_dir, side_name, source_context,
                          cohort['id'], SUITE_GEOMETRY)
    stdout_lines = artifacts['stdoutRaw'].decode('utf-8-sig').splitlines()
    receipt_lines = artifacts['receiptRaw'].decode('utf-8-sig').splitlines()
    receipt_observation = current_saved_adapters['extract_native'](
        receipt_lines, cohort, LAYOUT_VERSION, 'current', console=False)
    stdout_observation = current_saved_adapters['extract_native'](
        stdout_lines, cohort, LAYOUT_VERSION, 'current', console=True)
    need(stdout_observation['nativeLines'] == receipt_observation['nativeLines'],
         side_name + ' cohort stdout and receipt native bodies differ')
    need(artifacts['result']['elapsedSeconds'] * 1000 >=
         stdout_observation['summary']['nativeDiagnosticElapsedMs'],
         side_name + ' outer elapsed is shorter than the native cohort interval')
    return artifacts, stdout_observation


def _validate_compactness_run(run, spec_dir, side_name, source_context):
    artifacts = _read_run(run, spec_dir, side_name, source_context,
                          None, SUITE_COMPACTNESS)
    stdout_lines = artifacts['stdoutRaw'].decode('utf-8-sig').splitlines()
    receipt_lines = artifacts['receiptRaw'].decode('utf-8-sig').splitlines()
    criteria = [strict_json(line[len('PCB_COMPACTNESS_CRITERIA '):])
                for line in receipt_lines if line.startswith('PCB_COMPACTNESS_CRITERIA ')]
    cases = [strict_json(line[len('PCB_COMPACTNESS_CASE '):])
             for line in receipt_lines if line.startswith('PCB_COMPACTNESS_CASE ')]
    need(len(criteria) == 1 and len(cases) == 3,
         side_name + ' compactness run does not contain its criteria and three compact cases')
    summary_lines = [line for line in receipt_lines if line.startswith(COMPACTNESS_PASS_PREFIX)]
    compactness_assertions = validate_compactness_summary_lines(summary_lines, side_name)
    need(sum(line == COMPACTNESS_LEGACY_PASS for line in receipt_lines) == 1 and
         sum(line == FOCUSED_PASS for line in stdout_lines) == 1 and
         sum(line == CLEANUP_LINE for line in stdout_lines) == 1,
         side_name + ' compactness suite pass/cleanup markers are missing or duplicated')
    body = [line for line in stdout_lines if line.startswith('PCB_COMPACTNESS_') or
            line == COMPACTNESS_LEGACY_PASS or line.startswith(COMPACTNESS_PASS_PREFIX)]
    need(body == receipt_lines,
         side_name + ' compactness receipt differs from captured native report body')
    criteria = criteria[0]
    need(type(criteria) is dict,
         side_name + ' compactness criteria row is not an object')
    need(criteria.get('mode') == 'report' and
         criteria.get('scope') == 'FIXED_ACTUAL_RB56_GEOMETRY_REPORT_ONLY' and
         criteria.get('compactGoldenAppliesTo') == 'report' and
         criteria.get('rootSeeds') == COMPACTNESS_SEEDS and
         criteria.get('packages') == [40, 44, 60] and
         criteria.get('pads') == [96, 104, 143] and
         criteria.get('routingPolicy') == 'MEDIUM_BOARD@1' and
         criteria.get('admission') == 'RB56_BOARD_NORMAL@1' and
         criteria.get('densityThreshold') is None,
         side_name + ' compactness report identity or declared default envelope differs')
    validate_compactness_hash_tables(criteria, side_name)
    need(all(type(case) is dict for case in cases),
         side_name + ' compactness case rows are not objects')
    case_order = [(case.get('rootSeed'), case.get('placementInterpretationVersion'))
                  for case in cases]
    need(case_order == [(seed, 2) for seed in COMPACTNESS_SEEDS],
         side_name + ' compactness compact2 case identities/order differ')
    need(all(case.get('mode') == 'report' and
             case.get('routePolicy') == 'P07_FULLER_TWO_LAYER' and
             case.get('copperClearanceValidated') is True and
             case.get('admission') == 'RB56_BOARD_NORMAL@1' for case in cases),
         side_name + ' compactness case did not report the normal accepted physical route')
    return artifacts, criteria, cases, compactness_assertions


def stable_object(value, volatile):
    result = copy.deepcopy(value)
    for key in volatile:
        result.pop(key, None)
    return result


def compare_case_payloads(baseline_row, candidate_row):
    """Exact physical/metric parity, ignoring only invocation IDs and local timing."""
    return stable_object(baseline_row, VOLATILE_CASE_FIELDS) == stable_object(
        candidate_row, VOLATILE_CASE_FIELDS)


def require_case_parity(baseline_row, candidate_row, label):
    changed = _row_differences(baseline_row, candidate_row, VOLATILE_CASE_FIELDS)
    need(not changed, label + ' differs in fields: ' + ','.join(changed[:12]))


def compare_frame_and_summary(baseline_observation, candidate_observation):
    return (
        stable_object(baseline_observation['frame'], VOLATILE_FRAME_FIELDS) ==
        stable_object(candidate_observation['frame'], VOLATILE_FRAME_FIELDS) and
        stable_object(baseline_observation['summary'], VOLATILE_SUMMARY_FIELDS) ==
        stable_object(candidate_observation['summary'], VOLATILE_SUMMARY_FIELDS) and
        baseline_observation['footer'] == candidate_observation['footer']
    )


def _row_differences(left, right, volatile):
    a = stable_object(left, volatile)
    b = stable_object(right, volatile)
    keys = sorted(set(a) | set(b))
    return [key for key in keys if a.get(key, _MISSING) != b.get(key, _MISSING)]


def validate_compactness_payload(criteria, cases, side_name):
    need(type(criteria) is dict and type(cases) is list and len(cases) == 3,
         side_name + ' compactness report framing differs')
    need(criteria.get('mode') == 'report' and
         criteria.get('scope') == 'FIXED_ACTUAL_RB56_GEOMETRY_REPORT_ONLY' and
         criteria.get('compactGoldenAppliesTo') == 'report' and
         criteria.get('rootSeeds') == COMPACTNESS_SEEDS and
         criteria.get('packages') == [40, 44, 60] and
         criteria.get('pads') == [96, 104, 143] and
         criteria.get('routingPolicy') == 'MEDIUM_BOARD@1' and
         criteria.get('admission') == 'RB56_BOARD_NORMAL@1' and
         criteria.get('densityThreshold') is None,
         side_name + ' compactness report identity or declared default envelope differs')
    validate_compactness_hash_tables(criteria, side_name)
    need(all(type(case) is dict for case in cases),
         side_name + ' compactness case rows are not objects')
    case_order = [(case.get('rootSeed'), case.get('placementInterpretationVersion'))
                  for case in cases]
    need(case_order == [(seed, 2) for seed in COMPACTNESS_SEEDS],
         side_name + ' compactness compact2 case identities/order differ')
    need(all(case.get('mode') == 'report' and
             case.get('routePolicy') == 'P07_FULLER_TWO_LAYER' and
             case.get('copperClearanceValidated') is True and
             case.get('admission') == 'RB56_BOARD_NORMAL@1' for case in cases),
         side_name + ' compactness case did not report the normal accepted physical route')
    return criteria


def validate_compactness_hash_tables(criteria, side_name):
    for field in ['currentLayout25Compact2ExpectedHashes',
                  'currentLayout25Legacy1ExpectedHashes']:
        hashes = criteria.get(field)
        need(type(hashes) is list and len(hashes) == 3 and
             all(type(row) is dict and set(row) == {'rootSeed', 'sha256'} for row in hashes) and
             [row['rootSeed'] for row in hashes] == COMPACTNESS_SEEDS and
             all(type(row['sha256']) is str and
                 re.fullmatch(r'[0-9a-f]{64}', row['sha256']) for row in hashes),
             side_name + ' compactness literal geometry hash table is incomplete')


def validate_compactness_summary_lines(lines, side_name):
    need(type(lines) is list and len(lines) == 1,
         side_name + ' compactness completion summary is missing or duplicated')
    match = re.fullmatch(
        r'PASS: actual RB56 geometry report ([1-9][0-9]*) assertions '
        r'cases=3 mode=report scope=NO_DENSITY_ACCEPTANCE_THRESHOLD', lines[0])
    need(match is not None,
         side_name + ' compactness report summary/assertion census differs')
    return int(match.group(1))


def validate_complete_saved_census(cohort_counts, dynamic_checks, bound_failures):
    need(type(cohort_counts) is dict and list(cohort_counts) == COHORT_IDS and
         all(cohort_counts[key] == 12 for key in COHORT_IDS),
         'complete saved-profile cohort/case census differs')
    need(type(dynamic_checks) is dict and set(dynamic_checks) == {'baseline', 'candidate'},
         'same-side saved10 comparison sides differ')
    expected_dynamic = [
        (cohort['id'], seed)
        for cohort in saved.COHORTS
        for version, seed, _ in saved.cohort_case_order(cohort)
        if version == 11
    ]
    need(len(expected_dynamic) == 10,
         'frozen cohorts no longer contain the expected ten saved11 cases')
    for side in ('baseline', 'candidate'):
        items = dynamic_checks[side]
        need(type(items) is list and len(items) == 10 and
             all(type(item) is dict for item in items),
             side + ' same-side saved10 rows are not a complete object list')
        need(
             [(item.get('cohortId'), item.get('rootSeed')) for item in items] == expected_dynamic and
             all(item.get('status') == 'PASS_SAME_SIDE_SAVED10_OBSERVATION' and
                 item.get('operator') == '<=' and
                 integer(item.get('saved10Area'), 1, (1 << 63) - 1) and
                 integer(item.get('saved11Area'), 1, (1 << 63) - 1) and
                 item['saved11Area'] <= item['saved10Area']
                 for item in items),
             side + ' same-side saved10 identity/area comparisons differ or fail')
    need(type(bound_failures) is list and not bound_failures,
         'retained saved-profile comparative bounds failed')


def run_artifact_evidence(artifacts, native_interval_ms=None, case_count=None):
    result = artifacts['result']
    evidence = {
        'source': result['source'],
        'suite': result['suite'],
        'cohortId': result['cohortId'],
        'command': result['command'],
        'sourceSnapshotRevision': result['sourceSnapshotRevision'],
        'inputCount': result['inputCount'],
        'inputBytes': result['inputBytes'],
        'inputPinsSha256': result['inputPinsSha256'],
        'inputAudit': result['inputAudit'],
        'exitCode': result['exitCode'],
        'nativeExitCode': result['nativeExitCode'],
        'terminationProven': result['terminationProven'],
        'cleanupStatus': result['cleanupStatus'],
        'nativeLimitMs': result['nativeLimitMs'],
        'outerLimitMs': result['outerLimitMs'],
        'requestedExecutable': result['requestedExecutable'],
        'resultSha256': artifacts['resultSha256'],
        'stdoutBytes': result['stdoutBytes'],
        'stdoutSha256': artifacts['stdoutSha256'],
        'receiptBytes': result['receiptBytes'],
        'receiptSha256': artifacts['receiptSha256'],
        'stderrBytes': result['stderrBytes'],
        'stderrSha256': artifacts['stderrSha256'],
        'elapsedSeconds': result['elapsedSeconds'],
    }
    if native_interval_ms is not None:
        evidence['nativeDiagnosticElapsedMs'] = native_interval_ms
    if case_count is not None:
        evidence['caseCount'] = case_count
    return evidence


def source_review_rows(side_outputs):
    """Keep historical exact-hash review separate from enforced inequalities."""
    rows = []
    for side_name in ('baseline', 'candidate'):
        cohorts = side_outputs[side_name]['cohorts']
        for cohort_id in COHORT_IDS:
            for summarized in cohorts[cohort_id]['summarizedCases']:
                rows.append({
                    'sourceSide': side_name,
                    'cohortId': summarized['cohortId'],
                    'caseOrdinal': summarized['caseOrdinal'],
                    'placementInterpretationVersion': summarized['placementInterpretationVersion'],
                    'rootSeed': summarized['rootSeed'],
                    'oldHashStatus': summarized['oldHashStatus'],
                    'boardAreaBounds': summarized['boardAreaBounds'],
                    'otherSourceBounds': summarized['otherSourceBounds'],
                })
    return rows


def source_review_status_counts(review_rows):
    result = {}
    for side_name in ('baseline', 'candidate'):
        side_rows = [row for row in review_rows if row['sourceSide'] == side_name]
        hash_counts = {}
        area_counts = {}
        other_counts = {}
        for row in side_rows:
            status = row['oldHashStatus']
            hash_counts[status] = hash_counts.get(status, 0) + 1
            for check in row['boardAreaBounds']:
                status = check['status']
                area_counts[status] = area_counts.get(status, 0) + 1
            for check in row['otherSourceBounds']:
                status = check['status']
                other_counts[status] = other_counts.get(status, 0) + 1
        result[side_name] = {
            'caseCount': len(side_rows),
            'historicalExactHashStatusCounts': dict(sorted(hash_counts.items())),
            'retainedBoardAreaStatusCounts': dict(sorted(area_counts.items())),
            'retainedOtherSourceStatusCounts': dict(sorted(other_counts.items())),
        }
    return result


def validate_catalog_context(catalog_spec, spec_dir, baseline_map):
    exact_keys(catalog_spec, {'path', 'sha256', 'sourceRoot'}, 'catalog context')
    catalog_path = resolve_input(catalog_spec['path'], spec_dir, 'saved-profile catalog')
    catalog_raw = read_bounded(catalog_path, saved.MAX_CATALOG_BYTES, 'saved-profile catalog')
    need(type(catalog_spec['sha256']) is str and
         re.fullmatch(r'[0-9a-f]{64}', catalog_spec['sha256']) and
         sha_bytes(catalog_raw) == catalog_spec['sha256'],
         'saved-profile catalog digest differs')
    catalog = saved.validate_catalog_bytes(catalog_raw, catalog_spec['sha256'])
    need(catalog_spec['sha256'] == saved.CATALOG_SHA256,
         'saved-profile catalog differs from the immutable source-derived catalog')
    oracle_root = resolve_input(catalog_spec['sourceRoot'], spec_dir,
                                'catalog source root')
    need(oracle_root.is_dir(), 'catalog source root is not a directory')
    source_texts, arrays, bindings = saved.load_source_catalog(
        oracle_root, catalog, baseline_map)
    catalog_rows = saved.validate_catalog_rows(catalog, source_texts, arrays)
    return catalog_raw, catalog_rows, source_texts, arrays, bindings


def validate_side_spec(spec, side_name, spec_dir, source_context):
    exact_keys(spec, {
        'sourceRoot', 'pinsPath', 'pinsSha256', 'inputCount', 'inputBytes',
        'sourceSnapshotRevision', 'cohorts', 'compactnessRun',
    }, side_name + ' side')
    need(type(spec['cohorts']) is list and len(spec['cohorts']) == len(COHORT_IDS) and
         [item.get('cohortId') for item in spec['cohorts']] == COHORT_IDS,
         side_name + ' side must contain the six ordered saved-profile cohorts')
    return source_context


def run_validation(spec_path, spec_sha256):
    helper_digests = validate_helper_sources()
    current_saved_adapters, _adapter_proof = current_saved_profile_metadata_adapters(
        helper_digests)
    spec_path = Path(spec_path).resolve(strict=True)
    spec_raw = read_bounded(spec_path, MAX_SPEC_BYTES, 'parity input spec')
    need(type(spec_sha256) is str and re.fullmatch(r'[0-9a-f]{64}', spec_sha256) and
         sha_bytes(spec_raw) == spec_sha256, 'parity input spec digest differs')
    spec = strict_json(spec_raw.decode('utf-8-sig'))
    exact_keys(spec, {
        'schema', 'candidateId', 'baselineCommit', 'layoutVersion', 'dependencyEpoch', 'catalog',
        'sourceDelta', 'sides',
    }, 'parity spec')
    need(spec['schema'] == SCHEMA and type(spec['candidateId']) is str and
         re.fullmatch(r'[A-Za-z0-9_.:-]{1,96}', spec['candidateId']) and
         spec['baselineCommit'] == ACCEPTED_BASELINE_COMMIT and
         spec['layoutVersion'] == LAYOUT_VERSION and
         spec['dependencyEpoch'] == DEPENDENCY_EPOCH,
         'parity spec candidate identity/epoch differs')
    exact_keys(spec['sides'], {'baseline', 'candidate'}, 'parity sides')
    spec_dir = spec_path.parent
    baseline_context = load_pin_snapshot(spec['sides']['baseline'], spec_dir, 'baseline')
    candidate_context = load_pin_snapshot(spec['sides']['candidate'], spec_dir, 'candidate')
    source_delta = validate_source_delta(
        baseline_context, candidate_context, spec['sourceDelta'])
    catalog_raw, catalog_rows, source_texts, arrays, catalog_bindings = validate_catalog_context(
        spec['catalog'], spec_dir, baseline_context['pinMap'])
    need(all(path not in source_delta['changed'] and path in baseline_context['pinMap'] and
             path in candidate_context['pinMap'] for path in UNCHANGED_ORACLES),
         'required parity/oracle source input is missing or changed')

    side_outputs = {}
    bound_failures = []
    for side_name, source_context in (('baseline', baseline_context),
                                      ('candidate', candidate_context)):
        side_spec = spec['sides'][side_name]
        validate_side_spec(side_spec, side_name, spec_dir, source_context)
        cohorts = {}
        for cohort, run in zip(saved.COHORTS, side_spec['cohorts']):
            artifacts, observation = _validate_cohort_run(
                run, cohort, spec_dir, side_name, source_context,
                current_saved_adapters)
            summarized = []
            for (version, seed, ordinal), row in zip(
                    saved.cohort_case_order(cohort), observation['cases']):
                summarized.append(current_saved_adapters['validate_case'](
                    row, cohort, ordinal, version, seed, 'current',
                    catalog_rows[(cohort['id'], version, seed)], source_texts,
                    arrays, LAYOUT_VERSION, bound_failures))
            cohorts[cohort['id']] = {
                'observation': observation,
                'cases': observation['cases'],
                'summarizedCases': summarized,
                'artifacts': artifacts,
            }
        compact_artifacts, compact_criteria, compact_cases, compactness_assertions = _validate_compactness_run(
            side_spec['compactnessRun'], spec_dir, side_name, source_context)
        side_outputs[side_name] = {
            'cohorts': cohorts,
            'compactness': {
                'artifacts': compact_artifacts,
                'criteria': compact_criteria,
                'cases': compact_cases,
                'assertions': compactness_assertions,
            },
        }

    # Re-run the frozen same-side saved10 comparison oracle for both captures.
    dynamic_checks = {}
    paired_rows = {
        cohort_id: {
            'baseline': {'cases': side_outputs['baseline']['cohorts'][cohort_id]['summarizedCases']},
            'current': {'cases': side_outputs['candidate']['cohorts'][cohort_id]['summarizedCases']},
        }
        for cohort_id in COHORT_IDS
    }
    dynamic_checks['baseline'] = saved.compare_dynamic_saved10_areas(
        paired_rows, saved.COHORTS, catalog_rows, 'baseline', bound_failures)
    dynamic_checks['candidate'] = saved.compare_dynamic_saved10_areas(
        paired_rows, saved.COHORTS, catalog_rows, 'current', bound_failures)
    need(len(bound_failures) == 0,
         'one or more retained saved-profile comparative bounds failed')

    parity_cases = []
    case_total = 0
    for cohort_id in COHORT_IDS:
        left = side_outputs['baseline']['cohorts'][cohort_id]['observation']
        right = side_outputs['candidate']['cohorts'][cohort_id]['observation']
        need(compare_frame_and_summary(left, right),
             'default saved-profile frame/summary changed for ' + cohort_id)
        need(len(left['cases']) == len(right['cases']) == 12,
             'saved-profile case count differs for ' + cohort_id)
        for ordinal, (base_row, candidate_row) in enumerate(
                zip(left['cases'], right['cases']), 1):
            changed = _row_differences(base_row, candidate_row, VOLATILE_CASE_FIELDS)
            require_case_parity(base_row, candidate_row,
                                'default physical output ' + cohort_id + ' case ' + str(ordinal))
            parity_cases.append({
                'cohortId': cohort_id, 'caseOrdinal': ordinal,
                'placementInterpretationVersion': base_row['placementInterpretationVersion'],
                'rootSeed': base_row['rootSeed'],
                'baselineNormalizedPhysicalSha256': base_row['normalizedPhysicalSha256'],
                'candidateNormalizedPhysicalSha256': candidate_row['normalizedPhysicalSha256'],
                'defaultPhysicalParity': True,
            })
            case_total += 1

    base_compact = side_outputs['baseline']['compactness']
    candidate_compact = side_outputs['candidate']['compactness']
    need(stable_object(base_compact['criteria'], set()) ==
         stable_object(candidate_compact['criteria'], set()),
         'fixed compactness criteria/literal goldens changed between sources')
    need(base_compact['assertions'] == candidate_compact['assertions'],
         'fixed compactness report assertion count changed between sources')
    need(len(base_compact['cases']) == len(candidate_compact['cases']) == 3 and
         [case['rootSeed'] for case in base_compact['cases']] == COMPACTNESS_SEEDS and
         [case['rootSeed'] for case in candidate_compact['cases']] == COMPACTNESS_SEEDS,
         'fixed compactness native case census/order differs')
    compact_rows = []
    golden_hashes = {}
    for label, key in [('compact2', 'currentLayout25Compact2ExpectedHashes'),
                       ('legacy1', 'currentLayout25Legacy1ExpectedHashes')]:
        for row in base_compact['criteria'][key]:
            identity = (label, row['rootSeed'])
            need(identity not in golden_hashes, 'duplicate fixed golden identity')
            golden_hashes[identity] = row['sha256']
    need(len(golden_hashes) == 6,
         'the fixed compactness report did not bind all six literal default hashes')
    for baseline_case, candidate_case in zip(base_compact['cases'], candidate_compact['cases']):
        need(baseline_case == candidate_case,
             'fixed compactness case output changed for ' + baseline_case['rootSeed'])
        compact_rows.append({
            'rootSeed': baseline_case['rootSeed'],
            'placementInterpretationVersion': baseline_case['placementInterpretationVersion'],
            'compactnessNativeCaseEqual': True,
        })
    for interpretation, key in [
            (1, 'currentLayout25Legacy1ExpectedHashes'),
            (2, 'currentLayout25Compact2ExpectedHashes')]:
        for expected in base_compact['criteria'][key]:
            found = []
            for side_name in ('baseline', 'candidate'):
                for cohort_id in COHORT_IDS:
                    for case in side_outputs[side_name]['cohorts'][cohort_id]['cases']:
                        if (case['rootSeed'] == expected['rootSeed'] and
                                case['placementInterpretationVersion'] == interpretation):
                            found.append(case)
            # The six literal interpretations are independently executed by the
            # compactness suite; saved cohorts intentionally cover versions 3..12.
            if found:
                need(all(case['normalizedPhysicalSha256'] == expected['sha256']
                         for case in found),
                     'saved-profile geometry conflicts with a fixed default golden')

    case_counts = {key: len(side_outputs['baseline']['cohorts'][key]['cases'])
                   for key in COHORT_IDS}
    validate_complete_saved_census(case_counts, dynamic_checks, bound_failures)
    need(case_total == 72 and len(parity_cases) == 72,
         'complete 72-case/20-bound parity census was not evaluated')
    source_review = source_review_rows(side_outputs)
    need(len(source_review) == 144,
         'source-derived historical status review does not contain both 72-case sides')

    return {
        'protocol': OUTPUT_PROTOCOL,
        'status': 'PASS_DEFAULT_OUTPUT_PARITY_ONLY',
        'exitCode': 0,
        'candidateId': spec['candidateId'],
        'acceptedBaselineCommit': ACCEPTED_BASELINE_COMMIT,
        'layoutVersion': LAYOUT_VERSION,
        'dependencyEpoch': DEPENDENCY_EPOCH,
        'readerSourceSha256': helper_digests['read_fixed_prefix_default_parity.py'],
        'independentReaderSourceSha256': {
            name: digest for name, digest in helper_digests.items()
            if name != 'read_fixed_prefix_default_parity.py'
        },
        'sourceInputDelta': source_delta,
        'sourceSides': {
            name: {
                'inputCount': context['inputCount'],
                'inputBytes': context['inputBytes'],
                'sourceSnapshotRevision': context['sourceSnapshotRevision'],
                'pinsSha256': context['pinsSha256'],
                'cohortRuns': [
                    {
                        'cohortId': cohort_id,
                        **run_artifact_evidence(
                            context_output['cohorts'][cohort_id]['artifacts'],
                            context_output['cohorts'][cohort_id]['observation']['summary'][
                                'nativeDiagnosticElapsedMs'],
                            len(context_output['cohorts'][cohort_id]['cases'])),
                    }
                    for cohort_id in COHORT_IDS
                ],
                'compactnessRun': {
                    **run_artifact_evidence(context_output['compactness']['artifacts']),
                    'assertionCount': context_output['compactness']['assertions'],
                },
            }
            for name, context, context_output in [
                ('baseline', baseline_context, side_outputs['baseline']),
                ('candidate', candidate_context, side_outputs['candidate'])]
        },
        'fixedDefaultGeometry': {
            'scope': 'PcbCompactnessContractTest report mode, versions1/2, three literal roots',
            'compactnessCaseCountPerSide': len(base_compact['cases']),
            'assertionsPerSide': base_compact['assertions'],
            'allSixLiteralGeometryHashesPresent': True,
            'literalGeometryHashes': {
                'compact2': base_compact['criteria'][
                    'currentLayout25Compact2ExpectedHashes'],
                'legacy1': base_compact['criteria'][
                    'currentLayout25Legacy1ExpectedHashes'],
            },
            'baselineCompactnessOutputSha256': base_compact['artifacts']['stdoutSha256'],
            'candidateCompactnessOutputSha256': candidate_compact['artifacts']['stdoutSha256'],
            'compactnessCases': compact_rows,
        },
        'savedProfiles': {
            'cohortCount': len(COHORT_IDS),
            'caseCountPerSide': case_total,
            'caseParityCount': len(parity_cases),
            'pairedSaved10BoundsPerSide': 10,
            'pairedSaved10BoundsTotal': 20,
            'retainedBoundFailures': 0,
            'pairedSaved10Comparisons': dynamic_checks,
            'cases': parity_cases,
        },
        'sourceDerivedStatusReview': {
            'interpretation': 'Historical exact hashes are review statuses; only named retained inequalities are acceptance checks.',
            'statusCounts': source_review_status_counts(source_review),
            'caseStatuses': source_review,
        },
        'catalog': {
            'sha256': spec['catalog']['sha256'],
            'sourceBindings': catalog_bindings,
        },
        'scopeLimits': [
            'Exact default physical/metric parity for the two supplied layout25/dep27 source snapshots only.',
            'The six fixed hashes and 72 saved-profile cases do not establish a new saved-profile epoch transition.',
            'No layout admission, playable100, full native matrix, all-seed, or Player qualification is claimed.',
        ],
    }


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--spec', type=Path, required=True)
    parser.add_argument('--spec-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    need(not args.output.exists(), 'refusing to overwrite parity output')
    result = run_validation(args.spec, args.spec_sha256)
    output_path = args.output.resolve(strict=False)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, ensure_ascii=True, sort_keys=True,
                                separators=(',', ':')) + '\n')
    print('PASS: fixed-prefix default parity only; six literal geometries, 72 saved cases, 20 paired bounds')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError) as failure:
        message = str(failure)
        print('FAIL:', message[:1000])
        raise SystemExit(2)
