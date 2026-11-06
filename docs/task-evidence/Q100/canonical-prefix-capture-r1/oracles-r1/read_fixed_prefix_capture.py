#!/usr/bin/env python3
"""Validate the source-bound RB100 fixed-prefix capture and analyze its graphs.

This reader verifies capture lineage, resource limits, paired null/capture output,
and the frozen independent graph oracle.  It does not qualify a route, PCB, or
electrical design.
"""
import argparse
import hashlib
import json
import math
import os
import re
from pathlib import Path

import read_fixed_prefix_graph as graph_oracle


INPUT_SCHEMA = 'p07-fixed-prefix-capture-input-v1'
OUTPUT_SCHEMA = 'p07-fixed-prefix-capture-reader-result-v1'
RUN_SCHEMA = 'p07-fixed-prefix-native-run-v1'
SUITE = 'Rb100FixedPrefixCaptureContractTest'
ENTRY_SCRIPT = 'scripts/verify-current-contracts.ps1'
SOURCE_REVISION = 3
SOURCE_INPUT_COUNT = 1066
SOURCE_JAVA_COUNT = 850
CONTRACT_JAVA_COUNT = 211
LAYOUT_VERSION = 25
DEPENDENCY_EPOCH = 'tsj-generation-dependencies-v27'
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9
ROOT_SEED = '9007199254740993'
PLACEMENT_SEED = '-6587958367974952701'
ROUTING_SEED = '8445301674744508279'
MAX_INPUT_FILE_BYTES = 1024 * 1024
MAX_PIN_FILE_BYTES = 16 * 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_SOURCE_INPUT_BYTES = 512 * 1024 * 1024
MAX_SOURCE_INPUTS = 10000
MAX_HOLES = 390
MAX_ROUTE_ATTEMPTS = 800
MAX_ROUTE_EXPANSIONS = 160_000_000
MAX_P07_EXPANSIONS = 1_000_000
MAX_P07_BRANCH_EXPANSIONS = 100_000
MAX_P07_VIAS = 48
P07_GRID_PITCH = 10
MAX_OPERATION_MS = 90_000
MAX_CLEANUP_MS = 120_000
MAX_WORK_UNITS = 640
MAX_CAPTURE_WORK_UNITS = 4 * MAX_WORK_UNITS
MAX_UNIT_MS = 5000
MAX_OUTER_MS = 1_800_000
MAX_POLICY_CANONICAL_BYTES = 4 * 1024 * 1024
MAX_LINEAGE_FINGERPRINT_BYTES = 4 * 1024 * 1024
MAX_EVENT_LINE_BYTES = 16 * 1024 * 1024
PASS_PREFIX = 'PASS: RB100 fixed-prefix capture contracts '
OPERATION_SCOPE = 'OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY'
FACT_SCOPE = 'ACTUAL_GENERATOR_FIXED_PREFIX_OBSERVATION_ONLY'
PAIR_SCOPE = 'OBSERVATIONAL_NULL_CAPTURE_PARITY_ONLY'
MAX_P07_CANDIDATES = 80
MAX_GRID_CELLS = 250_000
MAX_PAD_COUNT = 246
FOCUSED_PASS = (
    'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.'
)
CLEANUP_LINE = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
GRAPH_TAG = 'P07_FIXED_PREFIX_GRAPH_DATA '
FACT_TAG = 'P07_FIXED_PREFIX_CAPTURE_FACT '
OPERATION_TAG = 'RB100_FIXED_PREFIX_CAPTURE_OPERATION '
PAIR_TAG = 'RB100_FIXED_PREFIX_CAPTURE_PAIR '
ORACLE_SOURCE_SHA256 = 'c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00'

OPERATION_ORDER = ['SHARED_NULL', 'SHARED_CAPTURE', 'SEPARATE_NULL', 'SEPARATE_CAPTURE']
VARIANTS = {
    'SHARED_HYSTERETIC_LINEAR': {
        'nullOperationId': 'SHARED_NULL',
        'captureOperationId': 'SHARED_CAPTURE',
        'graphCaseId': 'RB100_SHARED_HYSTERETIC_LINEAR_REQ0',
        'eligiblePlacementAttempts': [4, 2, 0, 1, 3],
    },
    'SEPARATE_DIRECT_SWITCHING': {
        'nullOperationId': 'SEPARATE_NULL',
        'captureOperationId': 'SEPARATE_CAPTURE',
        'graphCaseId': 'RB100_SEPARATE_DIRECT_SWITCHING_REQ0',
        'eligiblePlacementAttempts': [4, 2, 3, 0, 1],
    },
}
OPERATION_BY_ID = {
    operation_id: (variant, 'NULL' if operation_id.endswith('_NULL') else 'CAPTURE')
    for variant, pair in VARIANTS.items()
    for operation_id in (pair['nullOperationId'], pair['captureOperationId'])
}
EXPECTED_PRODUCTION_INPUTS = sorted([
    'src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java',
    'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',
    'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
])
RMO_PRODUCTION_INPUTS = {
    'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrdering.java',
    'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingChecks.java',
    'src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingRuntimeChecks.java',
}
NATIVE_CENSUS_STATIC_INPUTS = {
    'scripts/verify-current-contracts.ps1',
    'scripts/VerifierIsolation.psm1',
    'tests/contracts/current-contract-runner-contract.ps1',
    '.tools/gwt-2.7.0/gwt-user-2.7.0.jar',
    '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar',
}
REQUIRED_NATIVE_INPUTS = (set(EXPECTED_PRODUCTION_INPUTS) |
                           RMO_PRODUCTION_INPUTS |
                           NATIVE_CENSUS_STATIC_INPUTS | {
    'tests/contracts/Rb100PhysicalDiagnostics.java',
    'tests/contracts/Rb100FixedPrefixCaptureContractTest.java',
})
OPERATION_KEYS = {
    'protocolVersion', 'runId', 'operationId', 'variant', 'mode', 'status',
    'terminalStatus', 'policyOutcome', 'selectedRoutePolicy',
    'policySelectedPlacementAttempt', 'firstP07RequestAttempt',
    'routeEligiblePlacementAttempts', 'routeEligibilitySkips', 'routeOutcomes',
    'primaryRoutingOrders', 'primaryRoutingExpansions', 'policyCanonicalLength',
    'policyCanonicalSha256', 'policyCanonical', 'layoutOutput', 'components',
    'pads', 'nets', 'rootSeed', 'placementSeed', 'routingSeed', 'layoutVersion',
    'dependencyEpoch', 'planVersion', 'modelVersion', 'physicalIntentVersion',
    'operationMs', 'cleanupMs', 'cleanupStatus', 'workUnits', 'maxUnitMs',
    'failurePhase', 'failureType', 'cleanupFailureType', 'scope',
    'layoutQualification',
}
FACT_KEYS = {
    'protocolVersion', 'runId', 'operationId', 'mode', 'variant', 'rootSeed',
    'placementSeed', 'routingSeed', 'layoutVersion', 'dependencyEpoch',
    'planVersion', 'modelVersion', 'physicalIntentVersion', 'components', 'pads',
    'nets', 'routeEligiblePlacementAttempts', 'selectedPlacementIndex',
    'selectedPlacementAttempt', 'routeRequestIndex', 'rawPlacementFingerprint',
    'rawPlacementFingerprintLength', 'rawPlacementFingerprintSha256',
    'graphCaseId', 'graphSha256', 'graphBytes', 'gridWidth', 'gridHeight',
    'gridOriginX', 'gridOriginY', 'gridPitch', 'cellCount', 'primaryLayer',
    'startCell', 'initialFacesMask', 'pass', 'branchOrdinal', 'netId',
    'startPadId', 'rootPadId', 'startX', 'startY', 'rootX', 'rootY',
    'reachedPadIds', 'reachedPthPadIds', 'terminalReason',
    'prefixGeometryFingerprint', 'prefixGeometryFingerprintLength',
    'prefixGeometryFingerprintSha256', 'prefixHoleCount', 'prefixViaCount',
    'prefixHoleCountsByKind', 'prefixHoles', 'branchExpanded',
    'totalRouteExpansions', 'goalsTop', 'goalsBottom', 'captureWorkUnits',
    'copyEntries', 'goalProbes', 'ordinaryEdgeProbes', 'viaProbes',
    'comparisonEntries', 'captureActiveElapsedMillis',
    'serializationElapsedMillis', 'scope', 'reachabilityClaim',
    'layoutQualification',
}
PAIR_KEYS = {
    'protocolVersion', 'runId', 'variant', 'nullOperationId',
    'captureOperationId', 'eligiblePlacementAttemptsEqual',
    'firstP07RequestAttemptEqual', 'routeOutcomesEqual',
    'primaryRoutingOrdersEqual', 'primaryRoutingExpansionsEqual',
    'policyCanonicalAndTerminalEqual', 'layoutOutputEqual', 'status',
    'parityStatus', 'scope', 'layoutQualification',
}
SUMMARY_KEYS = {
    'status', 'operations', 'nullOperations', 'captureOperations', 'pairs',
    'graphs', 'authoredVariants', 'variants', 'runId', 'rootSeed',
    'placementSeed', 'routingSeed', 'layoutVersion', 'dependencyEpoch',
    'planVersion', 'modelVersion', 'physicalIntentVersion', 'assertions',
    'suiteMs', 'suiteLimitMs', 'scope', 'layoutQualification',
}
PAIR_EQUALITY_KEYS = [
    'eligiblePlacementAttemptsEqual', 'firstP07RequestAttemptEqual',
    'routeOutcomesEqual', 'primaryRoutingOrdersEqual',
    'primaryRoutingExpansionsEqual', 'policyCanonicalAndTerminalEqual',
    'layoutOutputEqual',
]
OPERATION_PARITY_FIELDS = [
    'terminalStatus', 'policyOutcome', 'selectedRoutePolicy',
    'policySelectedPlacementAttempt', 'firstP07RequestAttempt',
    'routeEligiblePlacementAttempts', 'routeEligibilitySkips', 'routeOutcomes',
    'primaryRoutingOrders', 'primaryRoutingExpansions', 'policyCanonicalLength',
    'policyCanonicalSha256', 'policyCanonical', 'layoutOutput', 'components',
    'pads', 'nets', 'rootSeed', 'placementSeed', 'routingSeed', 'layoutVersion',
    'dependencyEpoch', 'planVersion', 'modelVersion', 'physicalIntentVersion',
    'cleanupStatus', 'failurePhase', 'failureType', 'cleanupFailureType', 'scope',
    'layoutQualification',
]


class CaptureInputError(ValueError):
    """Sanitized input validation error; messages contain no private paths."""


def need(condition, message):
    if not condition:
        raise CaptureInputError(message)


def integer(value, minimum, maximum):
    return type(value) is int and minimum <= value <= maximum


def sha_bytes(raw):
    return hashlib.sha256(raw).hexdigest()


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'JSON_DUPLICATE_KEY')
        result[key] = value
    return result


def _reject_constant(_value):
    raise CaptureInputError('JSON_NONFINITE_NUMBER')


def strict_json(text):
    try:
        return json.loads(text, object_pairs_hook=_pairs_no_duplicates,
                          parse_constant=_reject_constant)
    except CaptureInputError:
        raise
    except (json.JSONDecodeError, RecursionError, UnicodeError, ValueError):
        raise CaptureInputError('JSON_INVALID') from None


def exact_keys(value, expected, label):
    need(type(value) is dict and set(value) == set(expected), label + '_KEYS_INVALID')


def safe_relative_path(value):
    need(type(value) is str and 0 < len(value) <= 4096, 'SOURCE_PATH_INVALID')
    normalized = value.replace('\\', '/')
    need(value == normalized and not normalized.startswith('/') and
         re.match(r'^[A-Za-z]:', normalized) is None and
         all(part not in ('', '.', '..') for part in normalized.split('/')),
         'SOURCE_PATH_INVALID')
    return normalized


def _is_reparse(path):
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, 'is_junction', None)
        if callable(is_junction) and is_junction():
            return True
    except OSError:
        return True
    try:
        file_attributes = getattr(path.stat(follow_symlinks=False), 'st_file_attributes', 0)
    except FileNotFoundError:
        # Symlinks and junctions were checked first, so an absent leaf is a
        # prospective destination unless one of those checks found a link.
        return False
    except OSError:
        return True
    return bool(file_attributes & 0x400)


def _has_reparse_component(path):
    absolute = Path(path).absolute()
    current = Path(absolute.anchor)
    for part in absolute.parts[1:]:
        current = current / part
        if _is_reparse(current):
            return True
    return False


def resolve_input(value, base, label):
    need(type(value) is str and 0 < len(value) <= 4096, label + '_PATH_INVALID')
    path = Path(value)
    if not path.is_absolute():
        path = Path(base) / path
    need(not _has_reparse_component(path), label + '_REPARSE_PATH')
    try:
        return path.resolve(strict=True)
    except (OSError, RuntimeError):
        raise CaptureInputError(label + '_PATH_UNAVAILABLE') from None


def read_bounded(path, limit, label, allow_empty=False):
    path = Path(path)
    try:
        if not path.is_file() or _is_reparse(path):
            raise CaptureInputError(label + '_NOT_REGULAR_FILE')
        size = path.stat().st_size
        need((0 <= size if allow_empty else 0 < size) and size <= limit,
             label + '_SIZE_INVALID')
        return path.read_bytes()
    except CaptureInputError:
        raise
    except OSError:
        raise CaptureInputError(label + '_READ_FAILED') from None


def validate_oracle_binding():
    path = Path(graph_oracle.__file__).resolve(strict=True)
    reader_directory = Path(__file__).resolve(strict=True).parent
    need(path.parent == reader_directory and path.name == 'read_fixed_prefix_graph.py' and
         not _is_reparse(path), 'GRAPH_ORACLE_SOURCE_LOCATION_INVALID')
    digest = sha_bytes(read_bounded(path, 4 * 1024 * 1024, 'GRAPH_ORACLE_SOURCE'))
    need(digest == ORACLE_SOURCE_SHA256, 'GRAPH_ORACLE_SOURCE_HASH_MISMATCH')
    return digest


def _closed_java_paths(source_root, relative_directory, recursive):
    directory = source_root.joinpath(*relative_directory.split('/'))
    need(not _has_reparse_component(directory), 'SOURCE_CENSUS_REPARSE_PATH')
    try:
        resolved_directory = directory.resolve(strict=True)
    except (OSError, RuntimeError):
        raise CaptureInputError('SOURCE_CENSUS_DIRECTORY_UNAVAILABLE') from None
    need(resolved_directory.is_dir(), 'SOURCE_CENSUS_DIRECTORY_INVALID')

    java_paths = set()
    pending = [directory]
    while pending:
        current = pending.pop()
        need(not _has_reparse_component(current), 'SOURCE_CENSUS_REPARSE_PATH')
        try:
            with os.scandir(current) as scan:
                entries = sorted(scan, key=lambda item: item.name)
        except OSError:
            raise CaptureInputError('SOURCE_CENSUS_DIRECTORY_UNAVAILABLE') from None
        for entry in entries:
            path = Path(entry.path)
            need(not _is_reparse(path), 'SOURCE_CENSUS_REPARSE_PATH')
            try:
                is_directory = entry.is_dir(follow_symlinks=False)
                is_file = entry.is_file(follow_symlinks=False)
            except OSError:
                raise CaptureInputError('SOURCE_CENSUS_ENTRY_UNAVAILABLE') from None
            need(is_directory or is_file, 'SOURCE_CENSUS_ENTRY_INVALID')
            if is_directory:
                if recursive:
                    pending.append(path)
                continue
            if not entry.name.endswith('.java'):
                continue
            need(not _has_reparse_component(path), 'SOURCE_CENSUS_REPARSE_PATH')
            try:
                resolved = path.resolve(strict=True)
                relative = resolved.relative_to(source_root).as_posix()
            except (OSError, RuntimeError, ValueError):
                raise CaptureInputError('SOURCE_CENSUS_ENTRY_UNAVAILABLE') from None
            need(resolved.is_file() and safe_relative_path(relative) == relative,
                 'SOURCE_CENSUS_ENTRY_INVALID')
            java_paths.add(relative)
    return java_paths


def _closed_native_input_paths(source_root):
    source_java = _closed_java_paths(source_root, 'src', recursive=True)
    contract_java = _closed_java_paths(source_root, 'tests/contracts', recursive=False)
    need(len(source_java) == SOURCE_JAVA_COUNT,
         'SOURCE_CENSUS_SOURCE_JAVA_COUNT_INVALID')
    need(len(contract_java) == CONTRACT_JAVA_COUNT,
         'SOURCE_CENSUS_CONTRACT_JAVA_COUNT_INVALID')

    paths = source_java | contract_java
    for relative in NATIVE_CENSUS_STATIC_INPUTS:
        path = source_root.joinpath(*relative.split('/'))
        need(not _has_reparse_component(path), 'SOURCE_CENSUS_REPARSE_PATH')
        try:
            resolved = path.resolve(strict=True)
            resolved_relative = resolved.relative_to(source_root).as_posix()
        except (OSError, RuntimeError, ValueError):
            raise CaptureInputError('SOURCE_CENSUS_STATIC_INPUT_UNAVAILABLE') from None
        need(resolved.is_file() and resolved_relative == relative,
             'SOURCE_CENSUS_STATIC_INPUT_INVALID')
        paths.add(relative)

    need(len(paths) == SOURCE_INPUT_COUNT, 'SOURCE_CENSUS_TOTAL_COUNT_INVALID')
    return paths


def validate_input_spec(spec, spec_dir):
    exact_keys(spec, {
        'schema', 'sourceRoot', 'pinsPath', 'pinsSha256', 'inputCount', 'inputBytes',
        'sourceSnapshotRevision', 'resultPath', 'stdoutPath', 'receiptPath', 'stderrPath',
    }, 'INPUT_SPEC')
    need(spec['schema'] == INPUT_SCHEMA and
         integer(spec['inputCount'], SOURCE_INPUT_COUNT, SOURCE_INPUT_COUNT) and
         integer(spec['inputBytes'], 1, MAX_SOURCE_INPUT_BYTES) and
         integer(spec['sourceSnapshotRevision'], SOURCE_REVISION, SOURCE_REVISION) and
         type(spec['pinsSha256']) is str and re.fullmatch(r'[0-9a-f]{64}', spec['pinsSha256']),
         'INPUT_SPEC_IDENTITY_INVALID')
    source_root = resolve_input(spec['sourceRoot'], spec_dir, 'SOURCE_ROOT')
    need(source_root.is_dir(), 'SOURCE_ROOT_NOT_DIRECTORY')
    pin_path = resolve_input(spec['pinsPath'], spec_dir, 'SOURCE_PINS')
    pins_raw = read_bounded(pin_path, MAX_PIN_FILE_BYTES, 'SOURCE_PINS')
    need(sha_bytes(pins_raw) == spec['pinsSha256'], 'SOURCE_PINS_HASH_MISMATCH')
    rows = strict_json(pins_raw.decode('utf-8-sig'))
    need(type(rows) is list and len(rows) == SOURCE_INPUT_COUNT, 'SOURCE_PINS_COUNT_INVALID')
    closed_paths = _closed_native_input_paths(source_root)
    pin_map = {}
    total_bytes = 0
    for row in rows:
        exact_keys(row, {'path', 'bytes', 'sha256'}, 'SOURCE_PIN')
        relative = safe_relative_path(row['path'])
        need(relative not in pin_map and integer(row['bytes'], 0, MAX_SOURCE_INPUT_BYTES) and
             type(row['sha256']) is str and re.fullmatch(r'[0-9a-f]{64}', row['sha256']),
             'SOURCE_PIN_INVALID')
        file_path = source_root.joinpath(*relative.split('/'))
        need(not _has_reparse_component(file_path), 'SOURCE_PIN_REPARSE_PATH')
        try:
            resolved = file_path.resolve(strict=True)
            actual_bytes = resolved.stat().st_size
            actual_sha = sha_bytes(resolved.read_bytes())
        except (OSError, RuntimeError):
            raise CaptureInputError('SOURCE_PIN_UNAVAILABLE') from None
        need(resolved.is_file() and actual_bytes == row['bytes'] and actual_sha == row['sha256'],
             'SOURCE_PIN_CONTENT_MISMATCH')
        total_bytes += actual_bytes
        need(total_bytes <= MAX_SOURCE_INPUT_BYTES, 'SOURCE_PIN_BYTES_OVERFLOW')
        pin_map[relative] = row['sha256']
    need(total_bytes == spec['inputBytes'], 'SOURCE_PIN_BYTE_COUNT_MISMATCH')
    need(REQUIRED_NATIVE_INPUTS <= set(pin_map), 'SOURCE_PINS_OMIT_REQUIRED_NATIVE_INPUT')
    need(set(pin_map) == closed_paths, 'SOURCE_PINS_CENSUS_MISMATCH')
    artifacts = {
        name: resolve_input(spec[name], spec_dir, name.upper())
        for name in ('resultPath', 'stdoutPath', 'receiptPath', 'stderrPath')
    }
    return {
        'sourceRoot': source_root, 'pinsPath': pin_path, 'pinsRaw': pins_raw,
        'pins': pin_map, 'inputCount': len(rows), 'inputBytes': total_bytes,
        'pinsSha256': sha_bytes(pins_raw), 'sourceSnapshotRevision': SOURCE_REVISION,
        'artifacts': artifacts,
    }


def expected_command():
    return {'entry': ENTRY_SCRIPT, 'suite': SUITE, 'args': [], 'environment': {}}


def validate_run_result(result, context, stdout_raw, receipt_raw, stderr_raw):
    required = {
        'schema', 'source', 'suite', 'cohortId', 'command', 'layoutVersion',
        'dependencyEpoch', 'sourceSnapshotRevision', 'inputCount', 'inputBytes',
        'inputPinsSha256', 'inputAudit', 'exitCode', 'nativeExitCode',
        'terminationProven', 'cleanupStatus', 'cleanupElapsedMs', 'nativeLimitMs',
        'outerLimitMs', 'processId', 'processStartTicks', 'elapsedSeconds',
        'requestedExecutable', 'stdoutBytes', 'stdoutSha256', 'stderrBytes',
        'stderrSha256', 'receiptBytes', 'receiptSha256',
    }
    need(type(result) is dict and required <= set(result), 'RUN_RESULT_FIELDS_MISSING')
    need(result.get('schema') == RUN_SCHEMA and result.get('source') == 'candidate' and
         result.get('suite') == SUITE and result.get('cohortId') is None and
         result.get('command') == expected_command(), 'RUN_RESULT_IDENTITY_INVALID')
    input_bytes = result.get('inputBytes')
    input_bytes_valid = (
        type(input_bytes) is int or
        (type(input_bytes) is float and math.isfinite(input_bytes) and input_bytes.is_integer())
    )
    need(result.get('layoutVersion') == LAYOUT_VERSION and
         result.get('dependencyEpoch') == DEPENDENCY_EPOCH and
         integer(result.get('sourceSnapshotRevision'), SOURCE_REVISION, SOURCE_REVISION) and
         result.get('inputCount') == context['inputCount'] and
         input_bytes_valid and input_bytes == context['inputBytes'],
         'RUN_RESULT_SOURCE_EPOCH_INVALID')
    need(result.get('inputPinsSha256') == context['pinsSha256'] and
         result.get('inputAudit') == 'PASS_BEFORE_AND_AFTER',
         'RUN_RESULT_SOURCE_BINDING_INVALID')
    need(integer(result.get('exitCode'), 0, 0) and
         integer(result.get('nativeExitCode'), 0, 0) and
         result.get('terminationProven') is True and
         result.get('cleanupStatus') == 'PASS' and result.get('cleanupElapsedMs') is None,
         'RUN_RESULT_EXIT_OR_CLEANUP_INVALID')
    need(result.get('nativeLimitMs') == 900000 and result.get('outerLimitMs') == MAX_OUTER_MS and
         result.get('requestedExecutable') == 'powershell.exe',
         'RUN_RESULT_BUDGET_INVALID')
    need(integer(result.get('processId'), 1, (1 << 63) - 1) and
         type(result.get('processStartTicks')) is str and
         re.fullmatch(r'[1-9][0-9]{0,19}', result['processStartTicks']) is not None and
         type(result.get('elapsedSeconds')) in (int, float) and
         math.isfinite(result['elapsedSeconds']) and 0 < result['elapsedSeconds'] <= 1800,
         'RUN_RESULT_PROCESS_INVALID')
    for label, raw in (('stdout', stdout_raw), ('receipt', receipt_raw), ('stderr', stderr_raw)):
        size = result.get(label + 'Bytes')
        digest = result.get(label + 'Sha256')
        need(integer(size, 0, MAX_CAPTURE_BYTES) and size == len(raw) and
             type(digest) is str and digest == sha_bytes(raw),
             'RUN_RESULT_' + label.upper() + '_BINDING_INVALID')


def decode_lines(raw, label):
    try:
        return raw.decode('utf-8-sig', errors='strict').splitlines()
    except UnicodeDecodeError:
        raise CaptureInputError(label + '_UTF8_INVALID') from None


def parse_tagged_json_line(line, prefix, label):
    need(type(line) is str and line.startswith(prefix), label + '_PREFIX_INVALID')
    body = line[len(prefix):]
    need(bool(body), label + '_BODY_MISSING')
    return strict_json(body)


def parse_graph_line(line):
    need(type(line) is str and line.startswith(GRAPH_TAG), 'GRAPH_LINE_PREFIX_INVALID')
    body = line[len(GRAPH_TAG):]
    try:
        raw = body.encode('utf-8', errors='strict')
    except UnicodeEncodeError:
        raise CaptureInputError('GRAPH_LINE_ENCODING_INVALID') from None
    need(0 < len(raw) <= graph_oracle.MAX_INPUT_BYTES, 'GRAPH_LINE_SIZE_INVALID')
    payload = strict_json(body)
    canonical = json.dumps(payload, sort_keys=True, separators=(',', ':'),
                           ensure_ascii=True, allow_nan=False).encode('ascii')
    need(raw == canonical, 'GRAPH_LINE_NOT_CANONICAL')
    try:
        graph = graph_oracle.parse_graph_bytes(raw)
    except graph_oracle.GraphInputError as error:
        raise CaptureInputError('GRAPH_' + error.code) from None
    return raw, payload, graph


def _java_string_length(value):
    try:
        return len(value.encode('utf-16-le', errors='strict')) // 2
    except UnicodeEncodeError:
        raise CaptureInputError('JAVA_STRING_INVALID') from None


def _java_string_hash(value):
    encoded = value.encode('utf-16-le', errors='strict')
    result = 0
    for offset in range(0, len(encoded), 2):
        unit = encoded[offset] | (encoded[offset + 1] << 8)
        result = (31 * result + unit) & 0xffffffff
    return result - (1 << 32) if result >= (1 << 31) else result


def _java_string_hash_hex(value):
    return format(_java_string_hash(value) & 0xffffffff, 'x')


def _signed_long_string(value, expected):
    need(type(value) is str and re.fullmatch(r'(?:0|-?[1-9][0-9]{0,18})', value) is not None and
         -(1 << 63) <= int(value) <= (1 << 63) - 1 and value == expected,
         'SIGNED_SEED_INVALID')


def _uuid_text(value):
    return type(value) is str and re.fullmatch(
        r'[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}', value
    ) is not None


def _validate_layout_output(value, label):
    common_keys = {
        'layoutReturned', 'zeroOutput', 'fingerprint', 'outline', 'rawSegments',
        'topTraceCount', 'bottomTraceCount', 'totalTraceCount', 'holeCount',
        'viaCount', 'platedHoleCount', 'nonPlatedHoleCount',
    }
    need(type(value) is dict, label + '_LAYOUT_OUTPUT_MISSING')
    exact_keys(value, common_keys | ({'fingerprintLength', 'fingerprintJavaHash'}
                                    if value.get('layoutReturned') is True else set()),
               label + '_LAYOUT_OUTPUT')
    need(type(value['layoutReturned']) is bool and type(value['zeroOutput']) is bool and
         value['layoutReturned'] != value['zeroOutput'], label + '_LAYOUT_OUTPUT_FLAGS_INVALID')
    count_names = ['rawSegments', 'topTraceCount', 'bottomTraceCount', 'totalTraceCount',
                   'holeCount', 'viaCount', 'platedHoleCount', 'nonPlatedHoleCount']
    for name in count_names:
        need(integer(value[name], 0, MAX_ROUTE_EXPANSIONS),
             label + '_LAYOUT_OUTPUT_COUNT_INVALID')
    need(value['totalTraceCount'] == value['topTraceCount'] + value['bottomTraceCount'] and
         value['holeCount'] == value['viaCount'] + value['platedHoleCount'] +
         value['nonPlatedHoleCount'], label + '_LAYOUT_OUTPUT_CENSUS_MISMATCH')
    if value['layoutReturned']:
        fingerprint = value['fingerprint']
        need(type(fingerprint) is str and 0 < len(fingerprint) <= MAX_LINEAGE_FINGERPRINT_BYTES and
             integer(value['fingerprintLength'], 1, MAX_LINEAGE_FINGERPRINT_BYTES) and
             _java_string_length(fingerprint) == value['fingerprintLength'] and
             type(value['fingerprintJavaHash']) is str and
             re.fullmatch(r'[0-9a-f]{1,8}', value['fingerprintJavaHash']) is not None and
             value['fingerprintJavaHash'] == _java_string_hash_hex(fingerprint),
             label + '_LAYOUT_FINGERPRINT_INVALID')
        outline = value['outline']
        exact_keys(outline, {'x', 'y', 'width', 'height'}, label + '_OUTLINE')
        need(integer(outline['x'], -(1 << 31), (1 << 31) - 1) and
             integer(outline['y'], -(1 << 31), (1 << 31) - 1) and
             integer(outline['width'], 1, (1 << 31) - 1) and
             integer(outline['height'], 1, (1 << 31) - 1),
             label + '_OUTLINE_INVALID')
    else:
        need(value['fingerprint'] is None and value['outline'] is None and
             all(value[name] == 0 for name in count_names),
             label + '_ZERO_OUTPUT_INVALID')
    return value


def _validate_operation(operation, operation_id, run_id):
    exact_keys(operation, OPERATION_KEYS, 'OPERATION')
    need(type(operation_id) is str and operation_id in OPERATION_BY_ID,
         'OPERATION_ID_UNKNOWN')
    variant, mode = OPERATION_BY_ID[operation_id]
    expected_attempts = VARIANTS[variant]['eligiblePlacementAttempts']
    need(integer(operation['protocolVersion'], 1, 1) and operation['runId'] == run_id and
         operation['operationId'] == operation_id and operation['variant'] == variant and
         operation['mode'] == mode and operation['status'] == 'PASS' and
         operation['terminalStatus'] in ('ROUTED', 'EXHAUSTED') and
         operation['policyOutcome'] in ('SUCCESS', 'REJECTED') and
         ((operation['terminalStatus'] == 'ROUTED' and
           operation['policyOutcome'] == 'SUCCESS') or
          (operation['terminalStatus'] == 'EXHAUSTED' and
           operation['policyOutcome'] == 'REJECTED')) and
         operation['layoutQualification'] is False,
         'OPERATION_IDENTITY_OR_STATUS_INVALID')
    need(operation['rootSeed'] == ROOT_SEED and operation['placementSeed'] == PLACEMENT_SEED and
         operation['routingSeed'] == ROUTING_SEED and
         all(type(operation[name]) is str for name in ('rootSeed', 'placementSeed', 'routingSeed')),
         'OPERATION_SEED_INVALID')
    need(integer(operation['layoutVersion'], LAYOUT_VERSION, LAYOUT_VERSION) and
         operation['dependencyEpoch'] == DEPENDENCY_EPOCH and
         integer(operation['planVersion'], PLAN_VERSION, PLAN_VERSION) and
         integer(operation['modelVersion'], MODEL_VERSION, MODEL_VERSION) and
         integer(operation['physicalIntentVersion'], PHYSICAL_INTENT_VERSION,
                 PHYSICAL_INTENT_VERSION),
         'OPERATION_EPOCH_INVALID')
    attempts = operation['routeEligiblePlacementAttempts']
    skips = operation['routeEligibilitySkips']
    outcomes = operation['routeOutcomes']
    need(type(attempts) is list and attempts == expected_attempts and
         len(attempts) == len(set(attempts)) and
         all(integer(value, 0, MAX_P07_CANDIDATES - 1) for value in attempts),
         'OPERATION_ELIGIBLE_ATTEMPTS_INVALID')
    need(type(skips) is list and len(skips) <= MAX_P07_CANDIDATES and
         all(type(value) is str and 0 < len(value) <= 1024 for value in skips),
         'OPERATION_ELIGIBILITY_SKIPS_INVALID')
    skipped_attempts = set()
    for value in skips:
        match = re.fullmatch(
            r'attempt=(0|[1-9][0-9]*);width=((?:0|-?[1-9][0-9]*));'
            r'height=((?:0|-?[1-9][0-9]*));cells=((?:0|-?[1-9][0-9]*));reason=GRID_LIMIT',
            value)
        need(match is not None, 'OPERATION_ELIGIBILITY_SKIP_FORMAT_INVALID')
        attempt, width, height, cells = (int(part) for part in match.groups())
        need(0 <= attempt < MAX_P07_CANDIDATES and attempt not in skipped_attempts and
             -(1 << 31) <= width <= (1 << 31) - 1 and
             -(1 << 31) <= height <= (1 << 31) - 1 and
             -(1 << 63) <= cells <= (1 << 63) - 1 and
             attempt not in attempts and cells == width * height and
             (width < 1 or height < 1 or cells > MAX_GRID_CELLS),
             'OPERATION_ELIGIBILITY_SKIP_LINEAGE_INVALID')
        skipped_attempts.add(attempt)
    need(type(outcomes) is list and len(outcomes) <= MAX_ROUTE_ATTEMPTS and
         all(type(value) is str and 0 < len(value) <= 4096 for value in outcomes),
         'OPERATION_ROUTE_OUTCOMES_INVALID')
    p07_attempts = []
    for outcome in outcomes:
        if outcome.startswith('P07_FULLER_TWO_LAYER@'):
            match = re.match(r'^P07_FULLER_TWO_LAYER@([0-9]+)=', outcome)
            need(match is not None, 'OPERATION_P07_OUTCOME_FORMAT_INVALID')
            p07_attempts.append(int(match.group(1)))
    need(p07_attempts and p07_attempts[0] == 4 and
         operation['firstP07RequestAttempt'] == 4,
         'OPERATION_FIRST_P07_ATTEMPT_INVALID')
    selected = operation['policySelectedPlacementAttempt']
    need(integer(selected, -1, 79) and (selected == -1 or selected in attempts) and
         (operation['policyOutcome'] != 'SUCCESS' or selected in attempts),
         'OPERATION_POLICY_SELECTED_ATTEMPT_INVALID')
    need(integer(operation['primaryRoutingOrders'], 0, MAX_ROUTE_ATTEMPTS * 3) and
         integer(operation['primaryRoutingExpansions'], 0, MAX_ROUTE_EXPANSIONS) and
         integer(operation['policyCanonicalLength'], 1, MAX_POLICY_CANONICAL_BYTES) and
         type(operation['policyCanonical']) is str and
         len(operation['policyCanonical'].encode('utf-8')) <= MAX_POLICY_CANONICAL_BYTES and
         _java_string_length(operation['policyCanonical']) == operation['policyCanonicalLength'] and
         type(operation['policyCanonicalSha256']) is str and
         re.fullmatch(r'[0-9a-f]{64}', operation['policyCanonicalSha256']) is not None and
         sha_bytes(operation['policyCanonical'].encode('utf-8')) == operation['policyCanonicalSha256'],
         'OPERATION_POLICY_CANONICAL_INVALID')
    need(integer(operation['components'], 100, 100) and
         integer(operation['pads'], MAX_PAD_COUNT, MAX_PAD_COUNT) and
         integer(operation['nets'], 1, MAX_PAD_COUNT),
         'OPERATION_LOGICAL_CENSUS_INVALID')
    _validate_layout_output(operation['layoutOutput'], 'OPERATION')
    if operation['terminalStatus'] == 'ROUTED':
        need(operation['selectedRoutePolicy'] == 'P07_FULLER_TWO_LAYER' and
             selected in attempts and operation['layoutOutput']['layoutReturned'] is True,
             'OPERATION_ROUTED_RESULT_INVALID')
    else:
        need(operation['selectedRoutePolicy'] is None and selected == -1 and
             operation['layoutOutput']['layoutReturned'] is False and
             operation['layoutOutput']['zeroOutput'] is True,
             'OPERATION_EXHAUSTED_RESULT_INVALID')
    need(integer(operation['operationMs'], 0, MAX_OPERATION_MS) and
         integer(operation['cleanupMs'], 0, MAX_CLEANUP_MS) and
         operation['cleanupStatus'] == 'PASS' and
         integer(operation['workUnits'], 0, MAX_WORK_UNITS) and
         integer(operation['maxUnitMs'], 0, MAX_UNIT_MS),
         'OPERATION_MEASURED_LIMIT_INVALID')
    for name in ('failurePhase', 'failureType', 'cleanupFailureType'):
        need(type(operation[name]) is str and operation[name] in ('', 'none'),
             'OPERATION_FAILURE_FIELD_NOT_EMPTY')
    need(operation['scope'] == OPERATION_SCOPE,
         'OPERATION_SCOPE_INVALID')
    return operation


def _grid_cell(x, y, origin_x, origin_y, pitch, width, height, label):
    dx, dy = x - origin_x, y - origin_y
    need(pitch > 0 and dx >= 0 and dy >= 0 and dx % pitch == 0 and dy % pitch == 0,
         label + '_GRID_ALIGNMENT_INVALID')
    cell_x, cell_y = dx // pitch, dy // pitch
    need(cell_x < width and cell_y < height, label + '_GRID_BOUNDS_INVALID')
    return cell_y * width + cell_x


def _validate_prefix_holes(fact):
    counts = fact['prefixHoleCountsByKind']
    exact_keys(counts, {'VIA', 'PLATED', 'NON_PLATED'}, 'PREFIX_HOLE_COUNTS')
    need(all(integer(counts[name], 0, MAX_HOLES) for name in counts),
         'PREFIX_HOLE_COUNTS_INVALID')
    holes = fact['prefixHoles']
    need(type(holes) is list and len(holes) <= MAX_HOLES,
         'PREFIX_HOLE_LIST_INVALID')
    actual = {'VIA': 0, 'PLATED': 0, 'NON_PLATED': 0}
    ids = set()
    for hole in holes:
        exact_keys(hole, {'id', 'netId', 'kind', 'x', 'y', 'drillRadius',
                          'landRadius', 'exposure'}, 'PREFIX_HOLE')
        need(type(hole['id']) is str and 0 < len(hole['id']) <= 512 and
             hole['id'] not in ids and type(hole['kind']) is str and
             hole['kind'] in actual and
             hole['exposure'] in ('EXPOSED', 'COVERED') and
             (hole['netId'] is None or (type(hole['netId']) is str and 0 < len(hole['netId']) <= 512)) and
             integer(hole['x'], -(1 << 31), (1 << 31) - 1) and
             integer(hole['y'], -(1 << 31), (1 << 31) - 1) and
             integer(hole['drillRadius'], 1, 100000) and
             integer(hole['landRadius'], 1, 100000) and
             ((hole['kind'] == 'NON_PLATED' and hole['netId'] is None and
               hole['drillRadius'] == hole['landRadius']) or
              (hole['kind'] != 'NON_PLATED' and hole['netId'] is not None and
               hole['drillRadius'] < hole['landRadius'])),
             'PREFIX_HOLE_FIELDS_INVALID')
        ids.add(hole['id'])
        actual[hole['kind']] += 1
    need(counts == actual and fact['prefixHoleCount'] == len(holes) and
         fact['prefixViaCount'] == actual['VIA'] and
         fact['prefixViaCount'] <= MAX_P07_VIAS, 'PREFIX_HOLE_CENSUS_MISMATCH')
    return actual


def _validate_fact(fact, graph_payload, graph, operation, run_id):
    exact_keys(fact, FACT_KEYS, 'CAPTURE_FACT')
    variant = operation['variant']
    need(type(variant) is str and variant in VARIANTS, 'CAPTURE_FACT_VARIANT_UNKNOWN')
    expected = VARIANTS[variant]
    need(integer(fact['protocolVersion'], 1, 1) and fact['runId'] == run_id and
         fact['operationId'] == operation['operationId'] and fact['mode'] == 'CAPTURE' and
         fact['variant'] == variant and fact['graphCaseId'] == expected['graphCaseId'] and
         fact['layoutQualification'] is False,
         'CAPTURE_FACT_IDENTITY_INVALID')
    for name, wanted in (('rootSeed', ROOT_SEED), ('placementSeed', PLACEMENT_SEED),
                         ('routingSeed', ROUTING_SEED)):
        _signed_long_string(fact[name], wanted)
    need(integer(fact['layoutVersion'], LAYOUT_VERSION, LAYOUT_VERSION) and
         fact['dependencyEpoch'] == DEPENDENCY_EPOCH and
         integer(fact['planVersion'], PLAN_VERSION, PLAN_VERSION) and
         integer(fact['modelVersion'], MODEL_VERSION, MODEL_VERSION) and
         integer(fact['physicalIntentVersion'], PHYSICAL_INTENT_VERSION,
                 PHYSICAL_INTENT_VERSION),
         'CAPTURE_FACT_EPOCH_INVALID')
    need(type(fact['routeEligiblePlacementAttempts']) is list and
         fact['routeEligiblePlacementAttempts'] == expected['eligiblePlacementAttempts'] and
         all(integer(value, 0, MAX_P07_CANDIDATES - 1)
             for value in fact['routeEligiblePlacementAttempts']) and
         integer(fact['selectedPlacementIndex'], 0, 0) and
         integer(fact['selectedPlacementAttempt'], 4, 4) and
         integer(fact['routeRequestIndex'], 0, 0),
         'CAPTURE_FACT_PLACEMENT_LINEAGE_INVALID')
    need(integer(fact['components'], 100, 100) and
         integer(fact['pads'], MAX_PAD_COUNT, MAX_PAD_COUNT) and
         integer(fact['nets'], 1, MAX_PAD_COUNT) and
         fact['components'] == operation['components'] and
         fact['pads'] == operation['pads'] and fact['nets'] == operation['nets'],
         'CAPTURE_FACT_LOGICAL_CENSUS_INVALID')
    raw_placement = fact['rawPlacementFingerprint']
    prefix_geometry = fact['prefixGeometryFingerprint']
    need(type(raw_placement) is str and 0 < len(raw_placement) <= MAX_LINEAGE_FINGERPRINT_BYTES and
         integer(fact['rawPlacementFingerprintLength'], 1, MAX_LINEAGE_FINGERPRINT_BYTES) and
         _java_string_length(raw_placement) == fact['rawPlacementFingerprintLength'] and
         sha_bytes(raw_placement.encode('utf-8')) == fact['rawPlacementFingerprintSha256'] and
         type(fact['rawPlacementFingerprintSha256']) is str and
         re.fullmatch(r'[0-9a-f]{64}', fact['rawPlacementFingerprintSha256']) is not None,
         'CAPTURE_RAW_PLACEMENT_FINGERPRINT_INVALID')
    need(type(prefix_geometry) is str and 0 < len(prefix_geometry) <= MAX_LINEAGE_FINGERPRINT_BYTES and
         integer(fact['prefixGeometryFingerprintLength'], 1, MAX_LINEAGE_FINGERPRINT_BYTES) and
         _java_string_length(prefix_geometry) == fact['prefixGeometryFingerprintLength'] and
         type(fact['prefixGeometryFingerprintSha256']) is str and
         re.fullmatch(r'[0-9a-f]{64}', fact['prefixGeometryFingerprintSha256']) is not None and
         sha_bytes(prefix_geometry.encode('utf-8')) == fact['prefixGeometryFingerprintSha256'],
         'CAPTURE_PREFIX_GEOMETRY_FINGERPRINT_INVALID')
    need(type(fact['graphSha256']) is str and graph.graph_sha256 == fact['graphSha256'] and
         integer(fact['graphBytes'], 1, graph_oracle.MAX_INPUT_BYTES) and
         fact['graphBytes'] == len(json.dumps(graph_payload, sort_keys=True, separators=(',', ':'),
                                              ensure_ascii=True, allow_nan=False).encode('ascii')) and
         fact['graphCaseId'] == graph.case_id and
         fact['gridWidth'] == graph.width and fact['gridHeight'] == graph.height and
         fact['cellCount'] == graph.cells and
         fact['primaryLayer'] == graph.primary and
         fact['gridWidth'] * fact['gridHeight'] == fact['cellCount'],
         'CAPTURE_GRAPH_LINEAGE_INVALID')
    for name, minimum, maximum in (
            ('gridWidth', 1, MAX_GRID_CELLS),
            ('gridHeight', 1, MAX_GRID_CELLS),
            ('gridOriginX', -(1 << 31), (1 << 31) - 1),
            ('gridOriginY', -(1 << 31), (1 << 31) - 1),
            ('gridPitch', P07_GRID_PITCH, P07_GRID_PITCH),
            ('cellCount', 1, MAX_GRID_CELLS),
            ('primaryLayer', 1, 1),
            ('startCell', 0, graph.cells - 1),
            ('initialFacesMask', 3, 3),
            ('pass', 0, 2),
            ('branchOrdinal', 1, MAX_PAD_COUNT),
            ('startX', -(1 << 31), (1 << 31) - 1),
            ('startY', -(1 << 31), (1 << 31) - 1),
            ('rootX', -(1 << 31), (1 << 31) - 1),
            ('rootY', -(1 << 31), (1 << 31) - 1),
            ('prefixHoleCount', 0, MAX_HOLES),
            ('prefixViaCount', 0, MAX_HOLES),
            ('branchExpanded', 0, MAX_P07_BRANCH_EXPANSIONS),
            ('totalRouteExpansions', 0, MAX_P07_EXPANSIONS),
            ('goalsTop', 0, graph.cells),
            ('goalsBottom', 0, graph.cells),
            # A generator advance may consume up to four internal capture chunks.
            # The containing operation remains independently capped at MAX_WORK_UNITS.
            ('captureWorkUnits', 1, MAX_CAPTURE_WORK_UNITS),
            ('copyEntries', 1, 32 * graph.state_count),
            ('goalProbes', 1, 8 * graph.state_count),
            ('ordinaryEdgeProbes', 1, 8 * graph.state_count),
            ('viaProbes', 1, 4 * graph.state_count),
            ('comparisonEntries', 1, 32 * graph.state_count),
            ('captureActiveElapsedMillis', 0, MAX_OPERATION_MS),
            ('serializationElapsedMillis', 0, MAX_OPERATION_MS)):
        need(integer(fact[name], minimum, maximum), 'CAPTURE_FACT_COUNTER_OR_COORDINATE_INVALID')
    # Each in-bounds directed neighbor is checked on both copper faces.
    expected_edge_probes = 4 * ((graph.width - 1) * graph.height +
                                graph.width * (graph.height - 1))
    need(fact['branchExpanded'] <= fact['totalRouteExpansions'] and
         fact['goalProbes'] == 2 * graph.cells and
         fact['ordinaryEdgeProbes'] == expected_edge_probes and
         fact['viaProbes'] == graph.cells and
         fact['captureWorkUnits'] <= 4 * operation['workUnits'] and
         fact['captureActiveElapsedMillis'] + fact['serializationElapsedMillis'] <=
         operation['operationMs'], 'CAPTURE_FACT_TIME_OR_EXPANSION_CENSUS_INVALID')
    for name in ('netId', 'startPadId', 'rootPadId', 'terminalReason'):
        need(type(fact[name]) is str and 0 < len(fact[name]) <= 1024,
             'CAPTURE_FACT_STRING_INVALID')
    need(fact['scope'] == FACT_SCOPE and fact['reachabilityClaim'] is False,
         'CAPTURE_FACT_SCOPE_OR_CLAIM_INVALID')
    need(type(fact['reachedPadIds']) is list and 0 < len(fact['reachedPadIds']) <= fact['pads'] and
         all(type(value) is str and 0 < len(value) <= 512 for value in fact['reachedPadIds']) and
         len(fact['reachedPadIds']) == len(set(fact['reachedPadIds'])) and
         type(fact['reachedPthPadIds']) is list and
         len(fact['reachedPthPadIds']) <= len(fact['reachedPadIds']) and
         all(type(value) is str and 0 < len(value) <= 512 for value in fact['reachedPthPadIds']) and
         len(fact['reachedPthPadIds']) == len(set(fact['reachedPthPadIds'])) and
         set(fact['reachedPthPadIds']) <= set(fact['reachedPadIds']) and
         fact['rootPadId'] in fact['reachedPadIds'] and
         fact['rootPadId'] not in fact['reachedPthPadIds'] and
         fact['terminalReason'] != 'FOUND',
         'CAPTURE_FACT_REACHED_PAD_LINEAGE_INVALID')
    _validate_prefix_holes(fact)

    start_cell = _grid_cell(fact['startX'], fact['startY'], fact['gridOriginX'],
                            fact['gridOriginY'], fact['gridPitch'], graph.width,
                            graph.height, 'START')
    root_cell = _grid_cell(fact['rootX'], fact['rootY'], fact['gridOriginX'],
                           fact['gridOriginY'], fact['gridPitch'], graph.width,
                           graph.height, 'ROOT')
    need(start_cell == fact['startCell'] and start_cell != root_cell,
         'CAPTURE_START_CELL_LINEAGE_INVALID')
    expected_starts = [face * graph.cells + start_cell for face in range(2)
                       if fact['initialFacesMask'] & (1 << face)]
    need(list(graph.starts) == expected_starts,
         'CAPTURE_GRAPH_STARTS_DO_NOT_MATCH_LINEAGE')
    top_goals = sum(1 for value in graph.goals[:graph.cells] if value)
    bottom_goals = sum(1 for value in graph.goals[graph.cells:] if value)
    need(top_goals == fact['goalsTop'] and bottom_goals == fact['goalsBottom'] and
         top_goals > 0 and bottom_goals > 0 and
         graph.goals[root_cell] and graph.goals[graph.cells + root_cell],
         'CAPTURE_GRAPH_GOALS_DO_NOT_MATCH_ROOT_LINEAGE')
    return fact, start_cell, root_cell


def _validate_pair(pair, variant, run_id, null_operation, capture_operation):
    exact_keys(pair, PAIR_KEYS, 'PAIR')
    variant_config = VARIANTS[variant]
    need(integer(pair['protocolVersion'], 1, 1) and pair['runId'] == run_id and
         pair['variant'] == variant and
         pair['nullOperationId'] == variant_config['nullOperationId'] and
         pair['captureOperationId'] == variant_config['captureOperationId'] and
         pair['status'] == 'PASS' and pair['parityStatus'] == 'PASS' and
         pair['scope'] == PAIR_SCOPE and
         pair['layoutQualification'] is False and
         all(pair[key] is True for key in PAIR_EQUALITY_KEYS),
         'PAIR_IDENTITY_OR_STATUS_INVALID')
    direct_equalities = {
        'eligiblePlacementAttemptsEqual': 'routeEligiblePlacementAttempts',
        'firstP07RequestAttemptEqual': 'firstP07RequestAttempt',
        'routeOutcomesEqual': 'routeOutcomes',
        'primaryRoutingOrdersEqual': 'primaryRoutingOrders',
        'primaryRoutingExpansionsEqual': 'primaryRoutingExpansions',
        'layoutOutputEqual': 'layoutOutput',
    }
    need(all(null_operation[left] == capture_operation[left]
             for left in OPERATION_PARITY_FIELDS), 'NULL_CAPTURE_OPERATION_PARITY_MISMATCH')
    need(all(null_operation[field] == capture_operation[field]
             for field in direct_equalities.values()), 'NULL_CAPTURE_PAIR_FIELDS_DIFFER')
    need(null_operation['policyCanonical'] == capture_operation['policyCanonical'] and
         null_operation['terminalStatus'] == capture_operation['terminalStatus'] and
         null_operation['policyOutcome'] == capture_operation['policyOutcome'],
         'NULL_CAPTURE_POLICY_TERMINAL_PARITY_MISMATCH')
    need(type(pair['scope']) is str,
         'PAIR_SCOPE_MISMATCH')
    return pair


def validate_operation_census(operations, facts, graphs, pairs, summary):
    need(type(operations) is list and all(type(operation) is dict for operation in operations) and
         type(facts) is list and all(type(fact) is dict for fact in facts) and
         type(graphs) is list and all(type(graph) is dict for graph in graphs) and
         type(pairs) is list and all(type(pair) is dict for pair in pairs),
         'OPERATION_GRAPH_PAIR_TYPES_INVALID')
    need([operation.get('operationId') for operation in operations] == OPERATION_ORDER,
         'OPERATION_ORDER_INVALID')
    need(len(operations) == 4 and len(facts) == 2 and len(graphs) == 2 and len(pairs) == 2,
         'OPERATION_GRAPH_PAIR_CENSUS_INVALID')
    run_ids = [operation.get('runId') for operation in operations]
    need(all(_uuid_text(value) for value in run_ids) and len(set(run_ids)) == 1,
         'OPERATION_RUN_ID_INVALID')
    run_id = run_ids[0]
    checked_operations = {
        operation['operationId']: _validate_operation(operation, operation['operationId'], run_id)
        for operation in operations
    }
    checked = []
    for variant in VARIANTS:
        config = VARIANTS[variant]
        null_operation = checked_operations[config['nullOperationId']]
        capture_operation = checked_operations[config['captureOperationId']]
        fact = next((item for item in facts if item.get('variant') == variant), None)
        graph_entry = next((item for item in graphs if item['graph'].case_id == config['graphCaseId']), None)
        pair = next((item for item in pairs if item.get('variant') == variant), None)
        need(fact is not None and graph_entry is not None and pair is not None,
             'VARIANT_CAPTURE_DATA_MISSING')
        checked_fact, start_cell, root_cell = _validate_fact(
            fact, graph_entry['payload'], graph_entry['graph'], capture_operation, run_id)
        _validate_pair(pair, variant, run_id, null_operation, capture_operation)
        oracle = graph_oracle.analyze_graph(graph_entry['graph']).to_record()
        need(oracle['acceptance'] == {
            'actualCaptureAttested': False, 'layout': False, 'electrical': False, 'q100': False,
        }, 'GRAPH_ORACLE_ACCEPTANCE_FLAGS_INVALID')
        checked.append({
            'variant': variant,
            'nullOperation': null_operation,
            'captureOperation': capture_operation,
            'fact': checked_fact,
            'graph': graph_entry['graph'],
            'graphPayload': graph_entry['payload'],
            'graphSha256': graph_entry['graph'].graph_sha256,
            'graphBytes': len(graph_entry['bytes']),
            'oracle': oracle,
            'startCell': start_cell,
            'rootCell': root_cell,
            'pair': pair,
        })
    exact_keys(summary, SUMMARY_KEYS, 'SUITE_SUMMARY')
    need(summary['status'] == 'PASS' and summary['runId'] == run_id and
         integer(summary['operations'], 4, 4) and integer(summary['nullOperations'], 2, 2) and
         integer(summary['captureOperations'], 2, 2) and integer(summary['pairs'], 2, 2) and
         integer(summary['graphs'], 2, 2) and integer(summary['authoredVariants'], 2, 2) and
         summary['variants'] == list(VARIANTS) and summary['rootSeed'] == ROOT_SEED and
         summary['placementSeed'] == PLACEMENT_SEED and summary['routingSeed'] == ROUTING_SEED and
         integer(summary['layoutVersion'], LAYOUT_VERSION, LAYOUT_VERSION) and
         summary['dependencyEpoch'] == DEPENDENCY_EPOCH and
         integer(summary['planVersion'], PLAN_VERSION, PLAN_VERSION) and
         integer(summary['modelVersion'], MODEL_VERSION, MODEL_VERSION) and
         integer(summary['physicalIntentVersion'], PHYSICAL_INTENT_VERSION,
                 PHYSICAL_INTENT_VERSION) and
         integer(summary['assertions'], 1, 10_000_000) and
         integer(summary['suiteMs'], 0, 900_000) and summary['suiteLimitMs'] == 900_000 and
         summary['scope'] == OPERATION_SCOPE and
         summary['layoutQualification'] is False and
         type(summary['scope']) is str,
         'SUITE_SUMMARY_INVALID')
    return run_id, checked


def _event_text_lines(receipt_lines):
    tags = (GRAPH_TAG, FACT_TAG, OPERATION_TAG, PAIR_TAG, PASS_PREFIX)
    return [line for line in receipt_lines if line.startswith(tags)]


def _validate_console_and_receipt(stdout_raw, receipt_raw):
    console_lines = decode_lines(stdout_raw, 'RUN_STDOUT')
    receipt_lines = decode_lines(receipt_raw, 'RUN_RECEIPT')
    need(receipt_lines and all(line for line in receipt_lines), 'RUN_RECEIPT_EMPTY_OR_BLANK')
    need(all(line.startswith((GRAPH_TAG, FACT_TAG, OPERATION_TAG, PAIR_TAG, PASS_PREFIX))
             for line in receipt_lines), 'RUN_RECEIPT_UNEXPECTED_LINE')
    console_native = _event_text_lines(console_lines)
    need(console_native == receipt_lines, 'RUN_CONSOLE_NATIVE_BODY_DIFFERS_FROM_RECEIPT')
    need(console_lines.count(FOCUSED_PASS) == 1 and
         console_lines.count(CLEANUP_LINE) == 1,
         'RUN_CONSOLE_PASS_OR_CLEANUP_MARKER_INVALID')
    need(sum(line.startswith(PASS_PREFIX) for line in receipt_lines) == 1,
         'RUN_SUITE_PASS_MARKER_INVALID')
    return console_lines, receipt_lines


def _parse_suite_summary(line, run_id):
    need(line.startswith(PASS_PREFIX), 'SUITE_PASS_MARKER_INVALID')
    summary = strict_json(line[len(PASS_PREFIX):])
    exact_keys(summary, SUMMARY_KEYS, 'SUITE_SUMMARY')
    need(summary['status'] == 'PASS' and summary['runId'] == run_id and
         integer(summary['operations'], 4, 4) and integer(summary['nullOperations'], 2, 2) and
         integer(summary['captureOperations'], 2, 2) and integer(summary['pairs'], 2, 2) and
         integer(summary['graphs'], 2, 2) and integer(summary['authoredVariants'], 2, 2) and
         summary['variants'] == list(VARIANTS) and summary['rootSeed'] == ROOT_SEED and
         summary['placementSeed'] == PLACEMENT_SEED and summary['routingSeed'] == ROUTING_SEED and
         integer(summary['layoutVersion'], LAYOUT_VERSION, LAYOUT_VERSION) and
         summary['dependencyEpoch'] == DEPENDENCY_EPOCH and
         integer(summary['planVersion'], PLAN_VERSION, PLAN_VERSION) and
         integer(summary['modelVersion'], MODEL_VERSION, MODEL_VERSION) and
         integer(summary['physicalIntentVersion'], PHYSICAL_INTENT_VERSION,
                 PHYSICAL_INTENT_VERSION) and
         integer(summary['assertions'], 1, 10_000_000) and
         integer(summary['suiteMs'], 0, 900_000) and summary['suiteLimitMs'] == 900_000 and
         summary['scope'] == OPERATION_SCOPE and
         summary['layoutQualification'] is False,
         'SUITE_SUMMARY_INVALID')
    return summary


def _parse_events(receipt_lines):
    events = []
    position = 0
    summary_line = None

    def take(prefix, label):
        nonlocal position
        need(position < len(receipt_lines) and receipt_lines[position].startswith(prefix),
             label + '_ORDER_OR_MISSING')
        line = receipt_lines[position]
        need(len(line.encode('utf-8')) <= MAX_EVENT_LINE_BYTES,
             label + '_LINE_TOO_LARGE')
        position += 1
        return line

    operations = []
    facts = []
    graphs = []
    pairs = []
    for variant in VARIANTS:
        config = VARIANTS[variant]
        null_line = take(OPERATION_TAG, 'NULL_OPERATION')
        operations.append(parse_tagged_json_line(null_line, OPERATION_TAG, 'OPERATION'))
        graph_line = take(GRAPH_TAG, 'GRAPH_DATA')
        graph_raw, graph_payload, graph = parse_graph_line(graph_line)
        graphs.append({'bytes': graph_raw, 'payload': graph_payload, 'graph': graph})
        fact_line = take(FACT_TAG, 'CAPTURE_FACT')
        facts.append(parse_tagged_json_line(fact_line, FACT_TAG, 'CAPTURE_FACT'))
        capture_line = take(OPERATION_TAG, 'CAPTURE_OPERATION')
        operations.append(parse_tagged_json_line(capture_line, OPERATION_TAG, 'OPERATION'))
        pair_line = take(PAIR_TAG, 'PAIR')
        pairs.append(parse_tagged_json_line(pair_line, PAIR_TAG, 'PAIR'))
        need(operations[-2].get('operationId') == config['nullOperationId'] and
             operations[-1].get('operationId') == config['captureOperationId'] and
             graphs[-1]['graph'].case_id == config['graphCaseId'],
             'VARIANT_EVENT_BINDING_INVALID')
    summary_line = take(PASS_PREFIX, 'SUITE_SUMMARY')
    need(position == len(receipt_lines), 'RUN_RECEIPT_TRAILING_EVENT')
    run_id = operations[0].get('runId')
    summary = _parse_suite_summary(summary_line, run_id)
    return operations, facts, graphs, pairs, summary


def _sanitize_layout_output(layout_output):
    if layout_output is None:
        return None
    return {
        'layoutReturned': layout_output['layoutReturned'],
        'zeroOutput': layout_output['zeroOutput'],
        'fingerprintSha256': (
            sha_bytes(layout_output['fingerprint'].encode('utf-8'))
            if layout_output['fingerprint'] is not None else None),
        'fingerprintLength': layout_output.get('fingerprintLength'),
        'fingerprintJavaHash': layout_output.get('fingerprintJavaHash'),
        'outline': layout_output['outline'],
        'rawSegments': layout_output['rawSegments'],
        'topTraceCount': layout_output['topTraceCount'],
        'bottomTraceCount': layout_output['bottomTraceCount'],
        'totalTraceCount': layout_output['totalTraceCount'],
        'holeCount': layout_output['holeCount'],
        'viaCount': layout_output['viaCount'],
        'platedHoleCount': layout_output['platedHoleCount'],
        'nonPlatedHoleCount': layout_output['nonPlatedHoleCount'],
    }


def _sanitize_operation(operation):
    return {
        'operationId': operation['operationId'], 'variant': operation['variant'],
        'mode': operation['mode'], 'status': operation['status'],
        'terminalStatus': operation['terminalStatus'], 'policyOutcome': operation['policyOutcome'],
        'selectedRoutePolicy': operation['selectedRoutePolicy'],
        'policySelectedPlacementAttempt': operation['policySelectedPlacementAttempt'],
        'firstP07RequestAttempt': operation['firstP07RequestAttempt'],
        'routeEligiblePlacementAttempts': operation['routeEligiblePlacementAttempts'],
        'routeEligibilitySkips': operation['routeEligibilitySkips'],
        'routeOutcomes': operation['routeOutcomes'],
        'primaryRoutingOrders': operation['primaryRoutingOrders'],
        'primaryRoutingExpansions': operation['primaryRoutingExpansions'],
        'policyCanonicalLength': operation['policyCanonicalLength'],
        'policyCanonicalSha256': operation['policyCanonicalSha256'],
        'layoutOutput': _sanitize_layout_output(operation['layoutOutput']),
        'components': operation['components'], 'pads': operation['pads'],
        'nets': operation['nets'], 'operationMs': operation['operationMs'],
        'cleanupMs': operation['cleanupMs'], 'cleanupStatus': operation['cleanupStatus'],
        'workUnits': operation['workUnits'], 'maxUnitMs': operation['maxUnitMs'],
        'failurePhase': operation['failurePhase'], 'failureType': operation['failureType'],
        'cleanupFailureType': operation['cleanupFailureType'],
    }


def _sanitize_fact(fact):
    return {
        key: fact[key]
        for key in (
            'protocolVersion', 'operationId', 'mode', 'variant', 'rootSeed',
            'placementSeed', 'routingSeed', 'layoutVersion', 'dependencyEpoch',
            'planVersion', 'modelVersion', 'physicalIntentVersion', 'components',
            'pads', 'nets', 'routeEligiblePlacementAttempts', 'selectedPlacementIndex',
            'selectedPlacementAttempt', 'routeRequestIndex',
            'rawPlacementFingerprintLength', 'rawPlacementFingerprintSha256',
            'graphCaseId', 'graphSha256', 'graphBytes', 'gridWidth', 'gridHeight',
            'gridOriginX', 'gridOriginY', 'gridPitch', 'cellCount', 'primaryLayer',
            'startCell', 'initialFacesMask', 'pass', 'branchOrdinal', 'netId',
            'startPadId', 'rootPadId', 'startX', 'startY', 'rootX', 'rootY',
            'reachedPadIds', 'reachedPthPadIds', 'terminalReason',
            'prefixGeometryFingerprintLength', 'prefixGeometryFingerprintSha256',
            'prefixHoleCount', 'prefixViaCount', 'prefixHoleCountsByKind',
            'prefixHoles', 'branchExpanded', 'totalRouteExpansions', 'goalsTop',
            'goalsBottom', 'captureWorkUnits', 'copyEntries', 'goalProbes',
            'ordinaryEdgeProbes', 'viaProbes', 'comparisonEntries',
            'captureActiveElapsedMillis', 'serializationElapsedMillis', 'scope',
            'reachabilityClaim', 'layoutQualification',
        )
    }


def run_validation(spec_path, spec_sha256):
    oracle_sha = validate_oracle_binding()
    spec_path = Path(spec_path).absolute()
    need(not _has_reparse_component(spec_path), 'INPUT_SPEC_REPARSE_PATH')
    spec_path = spec_path.resolve(strict=True)
    spec_raw = read_bounded(spec_path, MAX_INPUT_FILE_BYTES, 'INPUT_SPEC')
    need(type(spec_sha256) is str and re.fullmatch(r'[0-9a-f]{64}', spec_sha256) and
         sha_bytes(spec_raw) == spec_sha256, 'INPUT_SPEC_HASH_MISMATCH')
    spec = strict_json(spec_raw.decode('utf-8-sig'))
    context = validate_input_spec(spec, spec_path.parent)
    result_raw = read_bounded(context['artifacts']['resultPath'], MAX_RESULT_BYTES, 'RUN_RESULT')
    stdout_raw = read_bounded(context['artifacts']['stdoutPath'], MAX_CAPTURE_BYTES, 'RUN_STDOUT')
    receipt_raw = read_bounded(context['artifacts']['receiptPath'], MAX_CAPTURE_BYTES, 'RUN_RECEIPT')
    stderr_raw = read_bounded(context['artifacts']['stderrPath'], MAX_CAPTURE_BYTES, 'RUN_STDERR',
                              allow_empty=True)
    result = strict_json(result_raw.decode('utf-8-sig'))
    validate_run_result(result, context, stdout_raw, receipt_raw, stderr_raw)
    console_lines, receipt_lines = _validate_console_and_receipt(stdout_raw, receipt_raw)
    operations, facts, graphs, pairs, summary = _parse_events(receipt_lines)
    run_id, checked = validate_operation_census(operations, facts, graphs, pairs, summary)
    outputs = []
    for row in checked:
        outputs.append({
            'variant': row['variant'],
            'graph': {
                'caseId': row['graph'].case_id,
                'graphSha256': row['graphSha256'],
                'graphUtf8Bytes': row['graphBytes'],
                'width': row['graph'].width,
                'height': row['graph'].height,
                'cellCount': row['graph'].cells,
                'primaryLayer': row['graph'].primary,
                'startCell': row['startCell'],
                'rootCell': row['rootCell'],
                'independentRelaxedOracle': row['oracle'],
            },
            'pair': row['pair'],
            'nullOperation': _sanitize_operation(row['nullOperation']),
            'captureOperation': _sanitize_operation(row['captureOperation']),
            'captureFact': _sanitize_fact(row['fact']),
        })
    return {
        'schema': OUTPUT_SCHEMA,
        'status': 'PASS_CAPTURE_CONSISTENCY_ONLY',
        'runId': run_id,
        'readerSourceSha256': sha_bytes(Path(__file__).read_bytes()),
        'graphOracleSourceSha256': oracle_sha,
        'source': {
            'sourceSnapshotRevision': context['sourceSnapshotRevision'],
            'inputCount': context['inputCount'],
            'inputBytes': context['inputBytes'],
            'pinsSha256': context['pinsSha256'],
        },
        'nativeRun': {
            'schema': result['schema'], 'source': result['source'],
            'suite': result['suite'], 'command': result['command'],
            'layoutVersion': result['layoutVersion'],
            'dependencyEpoch': result['dependencyEpoch'],
            'inputAudit': result['inputAudit'], 'exitCode': result['exitCode'],
            'nativeExitCode': result['nativeExitCode'],
            'terminationProven': result['terminationProven'],
            'cleanupStatus': result['cleanupStatus'],
            'nativeLimitMs': result['nativeLimitMs'], 'outerLimitMs': result['outerLimitMs'],
            'elapsedSeconds': result['elapsedSeconds'],
            'resultSha256': sha_bytes(result_raw),
            'stdoutBytes': len(stdout_raw), 'stdoutSha256': sha_bytes(stdout_raw),
            'receiptBytes': len(receipt_raw), 'receiptSha256': sha_bytes(receipt_raw),
            'stderrBytes': len(stderr_raw), 'stderrSha256': sha_bytes(stderr_raw),
        },
        'summary': {
            'operationCount': len(operations), 'pairCount': len(pairs),
            'graphCount': len(graphs), 'variants': list(VARIANTS),
            'layoutQualification': False,
        },
        'variants': outputs,
        'acceptance': {
            'captureConsistency': True, 'routeAccepted': False,
            'layoutQualification': False, 'electricalQualification': False,
            'q100Qualification': False,
        },
        'scopeLimits': [
            'The independent result describes only each captured fixed graph in its history-relaxed model.',
            'Reachability is not route acceptance; unreachability is limited to that graph and model.',
            'This reader does not qualify layout, electrical behavior, playable100, or Q100.',
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
    output_path = Path(args.output).absolute()
    need(not _has_reparse_component(output_path), 'OUTPUT_REPARSE_PATH')
    need(not output_path.exists(), 'OUTPUT_ALREADY_EXISTS')
    result = run_validation(args.spec, args.spec_sha256)
    with output_path.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, ensure_ascii=True, sort_keys=True,
                                separators=(',', ':')) + '\n')
    print('PASS: fixed-prefix capture analysis only; no route or layout qualification')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except CaptureInputError as failure:
        print('FAIL:', str(failure)[:500])
        raise SystemExit(2)
    except (OSError, UnicodeError, TypeError, KeyError, IndexError, OverflowError,
            AttributeError, ValueError):
        print('FAIL: CAPTURE_INPUT_INVALID')
        raise SystemExit(2)
