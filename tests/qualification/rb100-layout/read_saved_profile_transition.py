"""Independently audit the finite saved-profile layout22 -> layout24 observations."""
import argparse
import bisect
import collections
import copy
import hashlib
import json
import math
import re
from pathlib import Path

from read_diagnostic import strict_json, need, integer
from read_geometry_transition import (
    normalized_geometry, normalizer_boundary, epoch_neutral_normalizer,
    diagnostic_source_boundary,
)

PROTOCOL = 'p07-saved-profile-epoch-v1'
RUN_SPEC_PROTOCOL = 'p07-saved-profile-epoch-run-spec-v1'
CATALOG_SCHEMA = 'troubleshootjs-q100-saved-profile-baseline-catalog'
CATALOG_SHA256 = '57b20a02b9f1f17671dd89a5a032cafbb792b76384cedbc85b5d69dc748d62a9'
BASELINE_PIN_FILE_SHA256 = '09ac49088fef03a2a0aee0929647f0e16b14bae15573faa89e71e19ad7d525da'
CURRENT_PIN_FILE_SHA256 = 'f7eea4e90710a8fb958fbf5ca039367c0abfaf6d5385dd529eae81e1eb9a6717'
P07_DIAGNOSTIC_KEY = 'tests/contracts/P07GeometryEpochDiagnosticContractTest.java'
P07_DIAGNOSTIC_BASELINE_SHA256 = '3c36b320364c39a162349cf814d9f1e0860586829b98f5956d19d7905e0caa46'
P07_DIAGNOSTIC_CURRENT_SHA256 = '3037bf6e7b7611184a312b36e0ef8924271b448d316dbce0d812078ddc38b889'
SCOPE = 'OBSERVATIONAL_SAVED_PROFILE_EPOCH_TRANSITION'
FRAME = 'P07_SAVED_PROFILE_EPOCH_FRAME '
CASE = 'P07_SAVED_PROFILE_EPOCH_CASE '
SUMMARY = 'P07_SAVED_PROFILE_EPOCH_SUMMARY '
FOOTER = 'PASS: P07 geometry epoch diagnostic contracts savedProfileEpoch=true '
HOST_PREFIX_LINES = [
    "WARNING: The names of some imported commands from the module 'VerifierIsolation' include unapproved verbs that might" + ' ',
    'make them less discoverable. To find the commands with unapproved verbs, run the Import-Module command again with the' + ' ',
    'Verbose parameter. For a list of approved verbs, type Get-Verb.',
    'JDK: javac 1.8.0_502',
    'PASS: current-contract runner pass detection assertions=16',
    '',
    'NATIVE BRIDGE: E06 scratch-only factory-name interning preserves GWT string-value dispatch; actual compiled factory gate remains required.',
    'NATIVE BRIDGE: scratch-only exact CirSim.console -> System.err.println; CircuitJS solver/models unchanged.',
    'NATIVE BRIDGE: E06 scratch-only ChipElm full-grid drawing preference (smallGrid=false); actual compiled constructor gate remains required.',
    '',
    'warning: [options] bootstrap class path not set in conjunction with -source 1.7',
    'Note: Some input files use unchecked or unsafe operations.',
    'Note: Recompile with -Xlint:unchecked for details.',
    '1 warning',
    '',
]
MAX_RECEIPT_BYTES = 16 * 1024 * 1024
MAX_CONSOLE_BYTES = 16 * 1024 * 1024
MAX_PIN_BYTES = 8 * 1024 * 1024
MAX_RESULT_BYTES = 1024 * 1024
MAX_NORMALIZER_BYTES = 2 * 1024 * 1024
MAX_CATALOG_BYTES = 512 * 1024
MAX_RUN_SPEC_BYTES = 2 * 1024 * 1024

REP_SEEDS = ['-1017', '-1004', '-1022', '0', '17', '42']
HOLDOUT_SEEDS = ['1', '7', '5', '14']
COHORTS = [
    {'id': 'SAVED_3_4_REP', 'group': 'representatives-v3-v4', 'cohort': 'representative',
     'versions': [3, 4], 'seeds': REP_SEEDS},
    {'id': 'SAVED_5_6_REP', 'group': 'representatives-v5-v6', 'cohort': 'representative',
     'versions': [5, 6], 'seeds': REP_SEEDS},
    {'id': 'SAVED_7_8_REP', 'group': 'representatives-v7-v8', 'cohort': 'representative',
     'versions': [7, 8], 'seeds': REP_SEEDS},
    {'id': 'SAVED_9_10_REP', 'group': 'representatives-v9-v10', 'cohort': 'representative',
     'versions': [9, 10], 'seeds': REP_SEEDS},
    {'id': 'SAVED_11_12_REP', 'group': 'representatives-v11-v12', 'cohort': 'representative',
     'versions': [11, 12], 'seeds': REP_SEEDS},
    {'id': 'SAVED_10_11_12_HOLDOUT', 'group': 'holdouts-v10-v11-v12', 'cohort': 'holdout',
     'versions': [10, 11, 12], 'seeds': HOLDOUT_SEEDS},
]
COHORT_BY_ID = {item['id']: item for item in COHORTS}
ROLE_WIDTHS = {'HIGH_CURRENT': 13, 'SUPPLY': 11, 'RETURN': 11, 'CONTROL': 9, 'SIGNAL': 9}
PRODUCTION_CHANGES = sorted([
    'src/com/lushprojects/circuitjs1/client/GenerationDependencyContext.java',
    'src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java',
    'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',
    'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
])
COMMON_CHANGED = sorted([
    'scripts/verify-current-contracts.ps1',
    *PRODUCTION_CHANGES,
    'tests/contracts/D01DiagnosticContractTest.java',
    'tests/contracts/P07GeometryEpochDiagnosticContractTest.java',
    'tests/contracts/PcbCanvasBoundaryContractTest.java',
    'tests/contracts/PcbCompactnessContractTest.java',
    'tests/contracts/PcbCorridorOrientationContractTest.java',
    'tests/contracts/PcbNormalPackingIdentityContractTest.java',
    'tests/contracts/Rb100P07BranchDiagnosticContractTest.java',
    'tests/contracts/Rb100P07PolicyDiagnosticContractTest.java',
    'tests/contracts/Rb56ModelIdentityContractTest.java',
    'tests/contracts/RelBSessionIdentityContractTest.java',
])
ADDED_TESTS = sorted([
    'tests/contracts/P07ReachedPthContractTest.java',
    'tests/contracts/PcbPlacementDiagnosticsContractTest.java',
    'tests/contracts/Rb100PhysicalDiagnostics.java',
    'tests/contracts/Rb100PhysicalReference.java',
])
REMOVED_TESTS = sorted([
    'tests/contracts/Rb100LayoutFeasibilityContractTest.java',
    'tests/contracts/Rb100LayoutFirstRoutingDiagnosticContractTest.java',
    'tests/contracts/Rb100LayoutPlacementDiagnosticContractTest.java',
    'tests/contracts/Rb100LayoutRoutingDiagnosticContractTest.java',
])
EQUAL_INPUTS = [
    'tests/contracts/P08ReferenceConductorBuilder.java',
    'tests/contracts/P08ScalabilityContractTest.java',
    'tests/contracts/P07LayeredRasterOracle.java',
    'tests/contracts/PcbFunctionalGroupingContractTest.java',
    'tests/contracts/PcbPocketPackingContractTest.java',
    'src/com/lushprojects/circuitjs1/client/Rb56Placement.java',
    'src/com/lushprojects/circuitjs1/client/PcbTraceRules.java',
    'scripts/VerifierIsolation.psm1',
    '.tools/gwt-2.7.0/gwt-user-2.7.0.jar',
    '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar',
]
EXPECTED_EQUAL_INPUT_ORACLE_HASHES = {
    'src/com/lushprojects/circuitjs1/client/Rb56Placement.java':
        '101ce980d753db359a9ed26fb66fed626d9f3945dcc4edc6350d8b8b8853cdfc',
    'src/com/lushprojects/circuitjs1/client/PcbTraceRules.java':
        'ffd7848e0016bc726d9098d583ba958c49395a72d95648250a2f6209e9f65149',
}
MISLOCATED_ORACLE_PATHS = {
    'tests/contracts/Rb56Placement.java',
    'tests/contracts/PcbTraceRules.java',
}


def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha_text(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def read_bounded(path, limit, label):
    path = Path(path)
    need(path.is_file(), label + ' is not a regular file')
    size = path.stat().st_size
    need(0 < size <= limit, label + ' exceeds its transport bound')
    return path.read_bytes()


def validate_console_preamble_lines(lines):
    need(lines == HOST_PREFIX_LINES,
         'console preamble differs from the exact frozen JDK/runner/bridge/compiler prefix')


def validate_console_preamble_bytes(raw):
    marker = FRAME.encode('ascii')
    need(raw.count(marker) == 1, 'console lacks one unique native frame boundary')
    frame_offset = raw.find(marker)
    need(frame_offset > 0 and raw[frame_offset - 1:frame_offset] == b'\n',
         'console native frame is not line-delimited after its host prefix')
    prefix = raw[:frame_offset]
    try:
        prefix_lines = prefix.decode('utf-8').splitlines()
    except UnicodeError as failure:
        raise ValueError('console host preamble is not UTF-8') from failure
    validate_console_preamble_lines(prefix_lines)
    return prefix


def uuid_text(value):
    return type(value) is str and re.fullmatch(
        r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', value) is not None


def canonical_signed_long(value):
    if type(value) is not str or re.fullmatch(r'-?(?:0|[1-9]\d*)', value) is None:
        return False
    number = int(value)
    return -(1 << 63) <= number <= (1 << 63) - 1 and str(number) == value


def cohort_case_order(cohort):
    return [(version, seed, ordinal)
            for version in cohort['versions']
            for seed_index, seed in enumerate(cohort['seeds'])
            for ordinal in [cohort['versions'].index(version) * len(cohort['seeds']) + seed_index + 1]]


def java_array(source, name):
    matches = re.findall(r'\b' + re.escape(name) + r'\s*=\s*\{([^{}]*)\}\s*;', source, re.S)
    need(len(matches) == 1, 'expected one source literal array: ' + name)
    body = re.sub(r'//[^\r\n]*', '', matches[0])
    values = []
    for token in body.split(','):
        token = token.strip()
        if not token:
            continue
        quoted = re.fullmatch(r'"([^"\\]*)"', token)
        if quoted:
            values.append(quoted.group(1))
        else:
            number = re.fullmatch(r'(-?\d+)[lL]?', token)
            need(number is not None, 'unrecognized Java array literal in ' + name)
            values.append(int(number.group(1)))
    need(values, 'empty Java source array: ' + name)
    return values


def java_expression(expression, arrays):
    match = re.fullmatch(r'([A-Za-z_]\w*)\[(\d+)\](?:\*(\d+))?', expression or '')
    need(match is not None, 'unsupported catalog literal expression: ' + str(expression))
    name, index = match.group(1), int(match.group(2))
    need(name in arrays and index < len(arrays[name]), 'catalog literal references missing source value')
    value = arrays[name][index]
    if match.group(3) is not None:
        need(type(value) is int, 'numeric multiplier applied to nonnumeric source literal')
        value *= int(match.group(3))
    return value


def source_arrays(source_id, source):
    names = {
        'PcbFunctionalGroupingContractTest': [
            'ROOTS', 'PART_COUNTS', 'PAD_COUNTS', 'SAVED6_AREA', 'SAVED7_AREA',
            'SAVED8_AREA', 'SAVED9_AREA', 'SAVED11_AREA', 'SAVED12_AREA',
            'SAVED12_LARGEST_VOID', 'SAVED12_GEOMETRY_SHA256',
            'SAVED12_HOLDOUT_AREA', 'SAVED12_HOLDOUT_LARGEST_VOID',
            'SAVED12_HOLDOUT_GEOMETRY_SHA256', 'SAVED8_GEOMETRY_SHA256',
            'SAVED6_GEOMETRY_SHA256', 'SAVED3_GEOMETRY_SHA256',
            'SAVED4_GEOMETRY_SHA256', 'SAVED5_GEOMETRY_SHA256',
            'ROLE_WIDTH_HOLDOUT_SEEDS',
        ],
        'PcbPocketPackingContractTest': [
            'roots', 'baselineAreas', 'saved11Areas', 'baselineHashes', 'saved11Hashes',
        ],
    }[source_id]
    return {name: java_array(source, name) for name in names}


def inline_v7_hash(source):
    matches = re.findall(
        r'String expected\s*=\s*index\s*==\s*2\s*\?\s*"([0-9a-f]{64})"\s*:\s*SAVED6_GEOMETRY_SHA256\[index\]\s*;',
        source)
    need(len(matches) == 1, 'version7 inline physical hash expression changed')
    return matches[0]


def inline_v10_v11_hash(source):
    matches = re.findall(
        r'index\s*==\s*3\s*\?\s*"([0-9a-f]{64})"\s*:\s*SAVED8_GEOMETRY_SHA256\[index\]',
        source)
    need(len(matches) == 2 and len(set(matches)) == 1,
         'version10/11 seed0 inline hash expressions changed')
    return matches[0]


def grouping_hash(version, seed_index, arrays, source):
    direct = {
        3: 'SAVED3_GEOMETRY_SHA256', 4: 'SAVED4_GEOMETRY_SHA256',
        5: 'SAVED5_GEOMETRY_SHA256', 6: 'SAVED6_GEOMETRY_SHA256',
        8: 'SAVED8_GEOMETRY_SHA256', 12: 'SAVED12_GEOMETRY_SHA256',
    }
    if version in direct:
        return arrays[direct[version]][seed_index]
    if version == 7:
        return inline_v7_hash(source) if seed_index == 2 else arrays['SAVED6_GEOMETRY_SHA256'][seed_index]
    if version == 9:
        return None if seed_index == 3 else arrays['SAVED8_GEOMETRY_SHA256'][seed_index]
    if version in (10, 11):
        if seed_index == 5:
            return None
        if seed_index == 3:
            return inline_v10_v11_hash(source)
        return arrays['SAVED8_GEOMETRY_SHA256'][seed_index]
    return None


def derived_old_hash(version, seed, seed_index, cohort, arrays_by_source, source):
    grouping_arrays = arrays_by_source['PcbFunctionalGroupingContractTest']
    pocket_arrays = arrays_by_source['PcbPocketPackingContractTest']
    if version == 12 and cohort['cohort'] == 'holdout':
        return grouping_arrays['SAVED12_HOLDOUT_GEOMETRY_SHA256'][seed_index]
    grouping = None if cohort['cohort'] == 'holdout' else grouping_hash(
        version, seed_index, grouping_arrays, source)
    pocket = None
    pocket_index = seed_index if cohort['cohort'] == 'representative' else 6 + seed_index
    if version == 10:
        pocket = pocket_arrays['baselineHashes'][pocket_index]
    elif version == 11:
        pocket = pocket_arrays['saved11Hashes'][pocket_index]
    values = [value for value in (grouping, pocket) if value is not None]
    need(len(set(values)) <= 1, 'source consumers disagree about the old normalized physical hash')
    return values[0] if values else None


def literal_hash_sources(row):
    found = set()
    expectations = row.get('sourceExpectations')
    need(type(expectations) is dict and set(expectations) == {
        'PcbFunctionalGroupingContractTest', 'PcbPocketPackingContractTest'},
        'catalog source expectation set differs')
    for source_id, entry in expectations.items():
        hashes = entry.get('literalGeometryHashes')
        if type(hashes) is list:
            for item in hashes:
                if item.get('status') == 'LITERAL':
                    found.add((source_id, item.get('constantOrInlineSource')))
    return found


def catalog_numeric_literal(check, source_id, row, source_texts, arrays_by_source):
    expression = check.get('literal')
    if expression == 'previousArea from same seed version10 result':
        need(source_id == 'PcbPocketPackingContractTest' and row.get('placementVersion') == 11 and
             check.get('sourceCondition') ==
                 'version==11; previousArea is observed version10 area, whose same source case has exact baselineAreas literal',
             'unexpected dynamic saved10-to-saved11 area comparison')
        index = row['seedIndex'] + (6 if row['cohort'] == 'holdout' else 0)
        expected = arrays_by_source[source_id]['baselineAreas'][index]
        need(check.get('value') == expected and check.get('operator') == '<=',
             'dynamic saved11 comparison differs from the source version10 literal bound')
        return expected, 'DYNAMIC_SAVED10_AREA'
    if expression == 'inline index==5 baseline 6103992':
        need(source_id == 'PcbFunctionalGroupingContractTest' and
             row.get('placementVersion') == 11 and row.get('cohort') == 'representative' and
             row.get('seedIndex') == 5 and
             check.get('sourceCondition') in {
                 'version==11; index==5 uses inline 6103992, otherwise SAVED9_AREA[index]',
                 'version==11 && index==5'},
             'unexpected inline saved11 area comparison')
        matches = re.findall(r'baseline\s*=\s*index\s*==\s*5\s*\?\s*(\d+)', source_texts[source_id])
        need(len(matches) == 1, 'source inline saved11 index5 area literal changed')
        expected = int(matches[0])
        need(check.get('value') == expected, 'catalog inline saved11 area differs from source')
        return expected, 'SOURCE_INLINE_LITERAL'
    expected = java_expression(expression, arrays_by_source[source_id])
    need(check.get('value') == expected, 'catalog numeric comparison differs from its source literal')
    return expected, 'SOURCE_ARRAY_LITERAL'


def verify_catalog_literal_records(row, source_texts, arrays):
    expected_hash_sources = set()
    for source_id, entry in row['sourceExpectations'].items():
        hashes = entry.get('literalGeometryHashes')
        if type(hashes) is list:
            for item in hashes:
                if item.get('status') == 'LITERAL':
                    expression = item.get('constantOrInlineSource')
                    if expression == 'inline conditional literal in measure()':
                        value = inline_v7_hash(source_texts[source_id])
                    elif expression == 'inline index==3 conditional literal in measure()':
                        value = inline_v10_v11_hash(source_texts[source_id])
                    elif expression and '[' in expression:
                        value = java_expression(expression, arrays[source_id])
                    else:
                        raise ValueError('unknown literal hash source expression')
                    need(item.get('value') == value, 'catalog hash literal differs from source array/expression')
                    expected_hash_sources.add((source_id, expression))
                else:
                    need(item.get('value') is None, 'nonliteral hash row unexpectedly contains a literal')
        for field in ['areaComparisons', 'protectedVoidComparisons']:
            checks = entry.get(field)
            if type(checks) is list:
                for check in checks:
                    catalog_numeric_literal(check, source_id, row, source_texts, arrays)
        reference = entry.get('protectedVoidReference')
        if type(reference) is dict and reference.get('constant'):
            value = java_expression(reference['constant'], arrays[source_id])
            need(reference.get('value') == value, 'catalog protected-void reference differs from source')
    serialized = literal_hash_sources(row)
    need(serialized == expected_hash_sources, 'catalog union hash sources differ from consumer records')
    catalog_sources = {(item.get('sourceId'), item.get('constantOrInlineSource'))
                       for item in row.get('oldHashSources', [])
                       if item.get('constantOrInlineSource') is not None}
    need(catalog_sources == expected_hash_sources,
         'catalog oldHashSources differs from source-derived literal consumers')


def load_source_catalog(root, catalog, baseline_pin_map):
    expected = {
        'PcbFunctionalGroupingContractTest': 'tests/contracts/PcbFunctionalGroupingContractTest.java',
        'PcbPocketPackingContractTest': 'tests/contracts/PcbPocketPackingContractTest.java',
    }
    entries = catalog.get('sources')
    need(type(entries) is list and {item.get('id') for item in entries} == set(expected),
         'catalog source pin set differs')
    root = Path(root).resolve(strict=True)
    need(root.is_dir(), 'baseline source snapshot root is not a directory')
    texts, maps, bindings = {}, {}, []
    for entry in entries:
        source_id = entry['id']
        need(entry.get('path') == expected[source_id] and entry.get('worktreeEqualsBase') is True,
             'catalog source binding is not the frozen test consumer')
        raw = read_bounded(root / expected[source_id], 2 * 1024 * 1024, source_id + ' source')
        source_sha = sha_bytes(raw)
        need(source_sha == entry.get('sha256') and len(raw) == entry.get('bytes') and
             baseline_pin_map.get(expected[source_id]) == source_sha,
             source_id + ' no longer matches the frozen catalog and baseline input pin')
        texts[source_id] = raw.decode('utf-8')
        maps[source_id] = source_arrays(source_id, texts[source_id])
        bindings.append({'sourceId': source_id, 'path': expected[source_id],
                         'sha256': source_sha, 'bytes': len(raw),
                         'boundToBaselineInputPins': True})
    grouping = texts['PcbFunctionalGroupingContractTest']
    pocket = texts['PcbPocketPackingContractTest']
    ga, pa = maps['PcbFunctionalGroupingContractTest'], maps['PcbPocketPackingContractTest']
    need(ga['ROOTS'] == [-1017, -1004, -1022, 0, 17, 42] and
         ga['PART_COUNTS'] == [40, 44, 60, 42, 53, 54] and
         ga['PAD_COUNTS'] == [96, 104, 143, 100, 129, 131] and
         ga['ROLE_WIDTH_HOLDOUT_SEEDS'] == [1, 7, 5, 14], 'source fixture inventory changed')
    need(pa['roots'] == [-1017, -1004, -1022, 0, 17, 42, 1, 7, 5, 14],
         'pocket source seed order changed')
    verify_literal_role_table(grouping)
    return texts, maps, bindings


def verify_literal_role_table(grouping_source):
    section_start = grouping_source.find('private static int literalRoleWidth(')
    need(section_start >= 0, 'saved role-width source table is missing')
    section = grouping_source[section_start:grouping_source.find('private static void check(', section_start)]
    cases = list(re.finditer(r'case\s+([A-Z_]+)\s*:', section))
    returns = list(re.finditer(r'return\s+(\d+)\s*;', section))
    derived, last_return = {}, -1
    for value in returns:
        pending = [match.group(1) for match in cases
                   if last_return < match.start() < value.start()]
        for role in pending:
            need(role not in derived, 'duplicate source role-width case: ' + role)
            derived[role] = int(value.group(1))
        last_return = value.end()
    need(derived == ROLE_WIDTHS, 'literal source role-width table differs')


def validate_catalog_bytes(raw, supplied_sha):
    actual = sha_bytes(raw)
    need(supplied_sha == CATALOG_SHA256 and actual == CATALOG_SHA256,
         'baseline catalog bytes do not match the frozen source-catalog digest')
    catalog = strict_json(raw.decode('utf-8-sig'))
    need(type(catalog) is dict and catalog.get('schema') == CATALOG_SCHEMA and
         catalog.get('schemaVersion') == 1 and
         catalog.get('scope') == 'IMMUTABLE_SOURCE_EXPECTATIONS_ONLY; NO_CURRENT_HASH_SEALING_OR_ROUTE_QUALIFICATION',
         'baseline catalog schema/scope differs')
    need(catalog.get('sourceBase', {}).get('commit') == '8a2c12446b2c46bde32b32537dec58a15a65d3b9' and
         catalog.get('sourceBase', {}).get('sourceFilesUnchangedAtBase') is True,
         'baseline catalog source base differs')
    order = catalog.get('cohortOrder', {})
    groups = order.get('groups')
    need(order.get('totalRows') == order.get('rowsPerSource') == 72 and
         type(groups) is list and len(groups) == 6, 'catalog is not the frozen 72-case cohort set')
    for actual_group, expected_group in zip(groups, COHORTS):
        need(actual_group.get('id') == expected_group['group'] and
             actual_group.get('cohort') == expected_group['cohort'] and
             actual_group.get('versionsInOrder') == expected_group['versions'] and
             actual_group.get('seedsInOrder') == expected_group['seeds'] and
             actual_group.get('rowCount') == 12, 'catalog cohort order differs')
    return catalog


def validate_catalog_rows(catalog, source_texts, arrays):
    rows = catalog.get('rows')
    need(type(rows) is list and len(rows) == 72, 'catalog does not contain exactly 72 saved-profile rows')
    result = {}
    expected_ordinal = 0
    for cohort in COHORTS:
        for version, seed, case_ordinal in cohort_case_order(cohort):
            expected_ordinal += 1
            row = rows[expected_ordinal - 1]
            seed_index = cohort['seeds'].index(seed)
            need(row.get('ordinal') == expected_ordinal and row.get('group') == cohort['group'] and
                 row.get('cohort') == cohort['cohort'] and row.get('placementVersion') == version and
                 row.get('seed') == seed and row.get('seedIndex') == seed_index,
                 'catalog case identity/order differs')
            expected_case_id = (('representative' if cohort['cohort'] == 'representative' else 'holdout') +
                                '-v' + str(version) + '-seed-' + seed)
            need(row.get('caseId') == expected_case_id, 'catalog case ID differs')
            expected_hash = derived_old_hash(version, seed, seed_index, cohort,
                                             arrays,
                                             source_texts['PcbFunctionalGroupingContractTest'])
            need(row.get('oldHash') == expected_hash and
                 row.get('oldHashStatus') == ('LITERAL' if expected_hash is not None else 'NO_EXACT_LITERAL'),
                 'catalog union old hash differs from independently parsed source consumers')
            verify_catalog_literal_records(row, source_texts, arrays)
            census = row.get('literalFixtureCensus')
            if cohort['cohort'] == 'representative':
                need(census.get('status') == 'LITERAL' and
                     census.get('componentCount') == arrays['PcbFunctionalGroupingContractTest']['PART_COUNTS'][seed_index] and
                     census.get('padCount') == arrays['PcbFunctionalGroupingContractTest']['PAD_COUNTS'][seed_index] and
                     census.get('sourceId') == 'PcbFunctionalGroupingContractTest',
                     'representative literal package/pad census differs from source')
            else:
                need(census.get('status') == 'NOT_DECLARED_FOR_HOLDOUT_BY_EITHER_SOURCE' and
                     census.get('componentCount') is None and census.get('padCount') is None and
                     census.get('derivation') == 'NOT_DERIVED_FROM_ROUTE_OUTPUT',
                     'holdout catalog improperly derives its fixture census from route output')
            result[(cohort['id'], version, seed)] = row
    return result


def resolve_input_path(value, repo_root, label):
    need(type(value) is str and 0 < len(value) <= 4096, 'invalid run-spec path: ' + label)
    path = Path(value)
    if not path.is_absolute():
        path = repo_root / path
    # Paths in unselected cohorts are declared in the run spec before their
    # native executions exist. Normalize them without requiring existence.
    return path.resolve(strict=False)


def load_run_spec(path, repo_root):
    raw = read_bounded(path, MAX_RUN_SPEC_BYTES, 'saved-profile run spec')
    spec = strict_json(raw.decode('utf-8-sig'))
    need(type(spec) is dict and spec.get('protocol') == RUN_SPEC_PROTOCOL,
         'saved-profile run spec protocol differs')
    rows = spec.get('cohorts')
    need(type(rows) is list and len(rows) == 6, 'run spec must declare exactly six cohorts')
    by_id = {}
    for entry in rows:
        need(type(entry) is dict and set(entry) == {'cohortId', 'baseline', 'current'} and
             entry.get('cohortId') in COHORT_BY_ID and
             entry['cohortId'] not in by_id, 'unknown or duplicate run-spec cohort')
        side_values = {}
        for side in ['baseline', 'current']:
            value = entry.get(side)
            need(type(value) is dict and set(value) == {'receipt', 'result', 'console', 'pins', 'normalizer'},
                 'run-spec side must bind receipt/result/console/pins/normalizer')
            side_values[side] = {
                key: resolve_input_path(value[key], repo_root, entry['cohortId'] + '/' + side + '/' + key)
                for key in ['receipt', 'result', 'console', 'pins', 'normalizer']}
        by_id[entry['cohortId']] = side_values
    need(set(by_id) == set(COHORT_BY_ID), 'run spec cohort set differs from frozen six')
    # Unselected cohort paths may not exist yet; only selected paths are opened.
    return spec, by_id, sha_bytes(raw)


def parse_trace_fingerprint(text):
    need(type(text) is str and 0 < len(text) <= 4 * 1024 * 1024,
         'missing bounded complete width-aware trace fingerprint')
    result, offset, sources = [], 0, set()
    while offset < len(text):
        length = re.match(r'(\d+):', text[offset:])
        need(length is not None, 'unknown trace-source length framing')
        offset += len(length[0])
        source_length = int(length[1])
        source = text[offset:offset + source_length]
        need(0 < source_length <= 1024 and source.isascii() and source not in sources,
             'duplicate or incomplete trace source identity')
        sources.add(source)
        offset += source_length
        header = re.match(r'(TOP|BOTTOM):(EXPOSED|COVERED):([^:;]+):', text[offset:])
        need(header is not None, 'unknown trace layer/exposure/net framing')
        offset += len(header[0])
        width = 9
        width_marker = re.match(r'W(\d+):', text[offset:])
        if width_marker:
            width = int(width_marker[1])
            need(width in [11, 13], 'noncanonical/default or unsupported width marker')
            offset += len(width_marker[0])
        points = []
        while True:
            point = re.match(r'(-?\d+),(-?\d+);', text[offset:])
            if point is None:
                break
            points.append((int(point[1]), int(point[2])))
            offset += len(point[0])
        need(len(points) >= 2 and all((a[0] == b[0]) != (a[1] == b[1])
             for a, b in zip(points, points[1:])), 'trace has empty or non-Manhattan copper')
        result.append({'source': source, 'layer': header[1], 'exposure': header[2],
                       'net': header[3], 'width': width, 'points': points})
    return result


def trace_segment_inventory(traces):
    segments = []
    normalized_segments = 0
    layer_counts = collections.Counter()
    for trace_index, trace in enumerate(traces):
        layer_counts[trace['layer']] += 1
        path = []
        for point in trace['points']:
            if path and path[-1] == point:
                continue
            if len(path) >= 2:
                a, b = path[-2:]
                if ((a[0] == b[0] == point[0] and (b[1] - a[1]) * (point[1] - b[1]) > 0) or
                    (a[1] == b[1] == point[1] and (b[0] - a[0]) * (point[0] - b[0]) > 0)):
                    path.pop()
            path.append(point)
        normalized_segments += len(path) - 1
        for first, second in zip(trace['points'], trace['points'][1:]):
            segments.append({
                'traceIndex': trace_index, 'layer': trace['layer'], 'net': trace['net'],
                'width': trace['width'], 'x1': first[0], 'y1': first[1],
                'x2': second[0], 'y2': second[1],
                'minX': min(first[0], second[0]), 'maxX': max(first[0], second[0]),
                'minY': min(first[1], second[1]), 'maxY': max(first[1], second[1]),
            })
    return segments, normalized_segments, dict(sorted(layer_counts.items()))


def segment_distance_squared(first, second):
    dx = max(0, first['minX'] - second['maxX'], second['minX'] - first['maxX'])
    dy = max(0, first['minY'] - second['maxY'], second['minY'] - first['maxY'])
    return dx * dx + dy * dy


def merge_lattice_intervals(intervals):
    merged = []
    for low, high in sorted(intervals):
        if not merged or low > merged[-1][1] + 1:
            merged.append([low, high])
        else:
            merged[-1][1] = max(merged[-1][1], high)
    return [(low, high) for low, high in merged]


def independent_layered_counts(components, traces, holes):
    """Count the P07 unit-centerline point union without enumerating each step."""
    horizontal_raw = collections.defaultdict(list)
    vertical_raw = collections.defaultdict(list)
    trace_steps = 0
    for trace in traces:
        layer = trace['layer']
        for (x1, y1), (x2, y2) in zip(trace['points'], trace['points'][1:]):
            dx, dy = x2 - x1, y2 - y1
            need((dx == 0) != (dy == 0), 'layered raster requires nonzero Manhattan segments')
            trace_steps += abs(dx) + abs(dy)
            if y1 == y2:
                horizontal_raw[(layer, y1)].append((min(x1, x2), max(x1, x2)))
            else:
                vertical_raw[(layer, x1)].append((min(y1, y2), max(y1, y2)))
    horizontal = {key: merge_lattice_intervals(value) for key, value in horizontal_raw.items()}
    vertical = {key: merge_lattice_intervals(value) for key, value in vertical_raw.items()}
    horizontal_count = sum(high - low + 1 for spans in horizontal.values() for low, high in spans)
    vertical_count = sum(high - low + 1 for spans in vertical.values() for low, high in spans)

    intersections = 0
    for layer in ['TOP', 'BOTTOM']:
        x_coordinates = sorted(x for item_layer, x in vertical if item_layer == layer)
        if not x_coordinates:
            continue
        events = []
        for (item_layer, y), spans in horizontal.items():
            if item_layer != layer:
                continue
            for low, high in spans:
                left = bisect.bisect_left(x_coordinates, low)
                right = bisect.bisect_right(x_coordinates, high)
                if left < right:
                    events.append((y, 1, left, right))
        for (item_layer, x), spans in vertical.items():
            if item_layer != layer:
                continue
            position = bisect.bisect_left(x_coordinates, x)
            for low, high in spans:
                # Query after all horizontal intervals at the upper row, and
                # before intervals at the lower row, to count inclusive rows.
                events.append((low, 0, position, -1))
                events.append((high, 2, position, 1))
        events.sort(key=lambda event: (event[0], event[1], event[2]))
        tree = [0] * (len(x_coordinates) + 1)

        def add(index, delta):
            while index < len(tree):
                tree[index] += delta
                index += index & -index

        def prefix(index):
            total = 0
            while index > 0:
                total += tree[index]
                index -= index & -index
            return total

        for _, order, first, second in events:
            if order == 1:
                add(first + 1, 1)
                if second < len(x_coordinates):
                    add(second + 1, -1)
            else:
                intersections += second * prefix(first + 1)

    def covered(layer, x, y):
        for intervals, fixed, varying in ((horizontal, y, x), (vertical, x, y)):
            spans = intervals.get((layer, fixed), [])
            starts = [span[0] for span in spans]
            position = bisect.bisect_right(starts, varying) - 1
            if position >= 0 and spans[position][0] <= varying <= spans[position][1]:
                return True
        return False

    discrete = set()
    for component in components:
        for pad in component['terminals']:
            need(pad['attachment'] == 'PLATED_THROUGH_HOLE',
                 'independent layered raster currently requires plated terminal barrels')
            for layer in ['TOP', 'BOTTOM']:
                point = (layer, pad['x'], pad['y'])
                if not covered(*point):
                    discrete.add(point)
    for hole in holes:
        if hole['kind'] == 'NON_PLATED':
            continue
        need(covered('TOP', hole['x'], hole['y']) and
             covered('BOTTOM', hole['x'], hole['y']),
             'plated via/hole center does not touch trace centerline on both faces')
        for layer in ['TOP', 'BOTTOM']:
            point = (layer, hole['x'], hole['y'])
            if not covered(*point):
                discrete.add(point)
    return horizontal_count + vertical_count - intersections + len(discrete), trace_steps


def independent_width_clearance(traces, segments):
    by_trace = collections.defaultdict(list)
    for segment in segments:
        by_trace[segment['traceIndex']].append(segment)
    pair_count = 0
    width_pairs = collections.Counter()
    max_clearance = 0
    for first_index, first in enumerate(traces):
        for second_index in range(first_index + 1, len(traces)):
            second = traces[second_index]
            if first['layer'] != second['layer'] or first['net'] == second['net']:
                continue
            a_segments, b_segments = by_trace[first_index], by_trace[second_index]
            pair_count += len(a_segments) * len(b_segments)
            pair_width = tuple(sorted((first['width'], second['width'])))
            width_pairs[pair_width] += len(a_segments) * len(b_segments)
            required = (first['width'] + second['width'] + 1) // 2 + 6
            max_clearance = max(max_clearance, required)
    # Sweep along x. All pairs with larger x separation are safely outside the
    # largest 13/13 threshold; the exact census above still includes every pair.
    ordered = sorted(segments, key=lambda item: (item['minX'], item['maxX'], item['traceIndex']))
    for index, first in enumerate(ordered):
        upper = first['maxX'] + max_clearance
        for later_index in range(index + 1, len(ordered)):
            second = ordered[later_index]
            if second['minX'] > upper:
                break
            if first['traceIndex'] == second['traceIndex'] or first['layer'] != second['layer'] or first['net'] == second['net']:
                continue
            required = (first['width'] + second['width'] + 1) // 2 + 6
            if second['maxY'] + required < first['minY'] or first['maxY'] + required < second['minY']:
                continue
            need(segment_distance_squared(first, second) >= required * required,
                 'independent pairwise finite-width copper clearance failed')
    return pair_count, {str(a) + '/' + str(b): count
                        for (a, b), count in sorted(width_pairs.items())}, max_clearance


def validate_trace_roles(traces, roles, version, native_census=None):
    width_census = collections.Counter()
    need(bool(traces), 'saved-profile copper trace census must be nonempty')
    for trace in traces:
        need(trace['net'] in roles, 'route uses an undeclared logical net')
        role = roles[trace['net']]
        expected_width = ROLE_WIDTHS[role] if version == 12 else 9
        need(trace['width'] == expected_width, 'trace width differs from its literal routing role')
        width_census[role + '@' + str(expected_width)] += 1
    canonical_census = dict(sorted(width_census.items()))
    if native_census is not None:
        need(type(native_census) is dict and native_census == canonical_census and
             all(integer(value, 1, len(traces)) for value in native_census.values()) and
             sum(width_census.values()) == len(traces), 'role-width polyline census differs')
    return canonical_census


def rectangles_are_valid(value, label):
    need(type(value) is dict and all(integer(value.get(key), -(1 << 31), (1 << 31) - 1)
         for key in ['x', 'y']) and all(integer(value.get(key), 1, (1 << 31) - 1)
         for key in ['width', 'height']), 'invalid ' + label + ' rectangle')


def source_metric(policy, metric, row):
    if metric == 'placementCandidates':
        match = re.search(r';placements=(\d+)', policy)
        need(match is not None, 'policy receipt lacks placement count')
        return int(match[1])
    if metric == 'oneFaceAttempts':
        match = re.search(r';p05=(\d+)/(\d+)', policy)
        need(match is not None, 'policy receipt lacks one-face attempt count')
        return int(match[1])
    if metric == 'twoLayerAttempts':
        match = re.search(r';p07=(\d+)/(\d+)', policy)
        need(match is not None, 'policy receipt lacks P07 attempt count')
        return int(match[1])
    if metric == 'placementDensityVersion':
        return row['placementDensityVersion']
    raise ValueError('unsupported saved-profile source metric: ' + str(metric))


def compare_operator(actual, operator, expected):
    if operator == '==': return actual == expected
    if operator == '<=': return actual <= expected
    if operator == '<': return actual < expected
    if operator == '>=': return actual >= expected
    if operator == '>': return actual > expected
    raise ValueError('unsupported old-source comparison operator: ' + str(operator))


def retained_metric_status(actual, expected, operator, side, label,
                           bound_failures=None, failure_context=None):
    need(side in {'baseline', 'current'}, 'unknown saved-profile metric side')
    if operator == '==' and side == 'current':
        return ('CURRENT_EXACT_METRIC_MATCH_NOT_ADOPTED' if actual == expected else
                'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW')
    if not compare_operator(actual, operator, expected):
        if (side == 'current' and bound_failures is not None and
                operator in {'<', '<=', '>', '>='}):
            need(type(bound_failures) is list and type(failure_context) is dict,
                 'retained-bound collection context is incomplete')
            failure = dict(failure_context)
            failure.update({'actual': actual, 'operator': operator, 'expected': expected,
                            'status': 'FAIL_RETAINED_BOUND'})
            bound_failures.append(failure)
            return 'FAIL_RETAINED_BOUND'
        need(False, side + ' violates retained source metric ' + label)
    return 'BASELINE_EXACT_METRIC_REPRODUCED' if operator == '==' else 'PASS'


def bound_failure_context(row, source_id, check, side, failure_kind):
    return {
        'failureKind': failure_kind,
        'cohortId': row.get('cohortId'),
        'caseOrdinal': row.get('caseOrdinal'),
        'sourceId': source_id,
        'metric': check.get('metric'),
        'literal': check.get('literal'),
        'sourceCondition': check.get('sourceCondition'),
        'side': side,
        'profile': row.get('placementInterpretation'),
        'placementInterpretationVersion': row.get('placementInterpretationVersion'),
        'placementDensityVersion': row.get('placementDensityVersion'),
        'seed': row.get('rootSeed'),
    }


def old_bound_checks(row, catalog_row, source_texts, arrays_by_source, side,
                     bound_failures=None):
    result = []
    for source_id, entry in catalog_row['sourceExpectations'].items():
        checks = entry.get('areaComparisons')
        for check in checks if type(checks) is list else []:
            expected, source_kind = catalog_numeric_literal(
                check, source_id, catalog_row, source_texts, arrays_by_source)
            actual = row.get('boardArea')
            if source_kind == 'DYNAMIC_SAVED10_AREA':
                result.append({'sourceId': source_id, 'metric': check['metric'],
                               'operator': check['operator'], 'literal': check['literal'],
                               'baselineLiteralCeiling': expected, 'actual': actual,
                               'status': 'DEFERRED_TO_SAME_SIDE_SAVED10_OBSERVATION'})
                continue
            need(integer(actual, 1, (1 << 63) - 1),
                 side + ' board area is not a typed integer')
            status = retained_metric_status(actual, expected, check['operator'], side,
                check['literal'], bound_failures, bound_failure_context(
                    row, source_id, check, side, 'RETAINED_BOARD_AREA_BOUND'))
            result.append({'sourceId': source_id, 'metric': check['metric'],
                           'operator': check['operator'], 'literal': check['literal'],
                           'expected': expected, 'actual': actual, 'status': status})
        void_checks = entry.get('protectedVoidComparisons')
        for check in void_checks if type(void_checks) is list else []:
            metric = check.get('metric')
            field = 'largestProtectedEmptyRectangleArea'
            expected, _ = catalog_numeric_literal(
                check, source_id, catalog_row, source_texts, arrays_by_source)
            actual = row[field]
            need(integer(actual, 0, (1 << 63) - 1),
                 side + ' protected-void metric is not a typed integer')
            status = retained_metric_status(actual, expected, check['operator'], side,
                check['literal'], bound_failures, bound_failure_context(
                    row, source_id, check, side, 'RETAINED_PROTECTED_VOID_BOUND'))
            result.append({'sourceId': source_id, 'metric': metric,
                           'operator': check['operator'], 'literal': check['literal'],
                           'expected': expected, 'actual': actual, 'status': status})
        reference = entry.get('protectedVoidReference')
        if type(reference) is dict and reference.get('constant'):
            # The catalog explicitly identifies v12 representative values as
            # published references for a later source path, not assertions in
            # this saved-profile caller. Preserve the measured comparison in
            # the output without silently promoting that reference to a gate.
            expected = java_expression(reference['constant'], arrays_by_source[source_id])
            need(reference.get('value') == expected, 'catalog protected-void reference differs from source')
            if row.get('placementInterpretationVersion', 0) == 12:
                result.append({'sourceId': source_id, 'metric': 'largestProtectedEmptyRectangleArea',
                               'literal': reference['constant'], 'expected': expected,
                               'actual': row.get('largestProtectedEmptyRectangleArea'),
                               'status': 'REFERENCE_ONLY_NOT_ASSERTED_BY_SAVED_CALLER'})
    return result


def check_other_source_bounds(row, catalog_row):
    policy = row['policyStatisticsCanonical']
    results = []
    for source_id, entry in catalog_row['sourceExpectations'].items():
        checks = entry.get('otherLiteralConditions')
        for check in checks if type(checks) is list else []:
            metric = check.get('metric')
            if metric not in {'placementCandidates', 'oneFaceAttempts', 'twoLayerAttempts', 'placementDensityVersion'}:
                continue
            actual = source_metric(policy, metric, row)
            expected = check['value']
            need(compare_operator(actual, check['operator'], expected),
                 'retained source policy counter fails: ' + metric)
            results.append({'sourceId': source_id, 'metric': metric,
                            'operator': check['operator'], 'expected': expected,
                            'actual': actual, 'status': 'PASS'})
    return results


def validate_case(row, cohort, case_ordinal, version, seed, side,
                  catalog_row, source_texts, arrays_by_source, layout_version,
                  bound_failures=None):
    dependency = 'tsj-generation-dependencies-v' + str(layout_version + 1)
    need(type(row) is dict and row.get('protocol') == PROTOCOL and
         row.get('status') == 'PASS_OBSERVATION_ONLY' and row.get('scope') == SCOPE and
         row.get('cohortId') == cohort['id'] and integer(row.get('caseOrdinal'), case_ordinal, case_ordinal) and
         integer(row.get('caseCount'), 12, 12) and row.get('caseOrder') ==
         'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER' and
         row.get('rootSeed') == seed and integer(row.get('placementInterpretationVersion'), version, version) and
         row.get('placementInterpretation') == 'SAVED_PROFILE_' + str(version) and
         integer(row.get('layoutVersion'), layout_version, layout_version) and
         row.get('dependencyEpoch') == dependency and
         integer(row.get('planVersion'), 1, 1) and row.get('modelDumpEpoch') ==
         'circuitjs-source-load-model-inputs-no-transient-dump-v7' and
         row.get('layoutQualification') is False and row.get('adopted') is False and
         row.get('adoption') is False and
         row.get('baselineExpected') == 'EXTERNAL_SAVED_PROFILE_CATALOG' and
         row.get('baselineExpectedBasis') == 'INDEPENDENT_SOURCE_DERIVED_CATALOG_READER' and
         uuid_text(row.get('runId')) and uuid_text(row.get('frameId')),
         'saved-profile case identity, scope, or epoch differs')
    need(integer(row.get('nativeDiagnosticElapsedMs'), 0, 180000) and
         row.get('timingScope') == 'NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING',
         'saved-profile native diagnostic timing exceeds its test-only bound')
    need(row.get('physicalPolicyId') == 'MEDIUM_BOARD' and
         integer(row.get('physicalPolicyVersion'), 1, 1) and
         row.get('routingLayer') == 'BOTTOM' and row.get('requiresTwoLayerRouting') is True and
         integer(row.get('placementDensityVersion'), 11 if version == 12 else version,
                 11 if version == 12 else version) and
         integer(row.get('placementPackingVersion'), 1, 1) and
         row.get('traceWidthProfile') == ('ROLE_TRACE@1' if version == 12 else 'UNIFORM_TRACE@1') and
         row.get('traceWidthValidation') == 'PASS_INDEPENDENT_LITERAL_ROLE_TABLE' and
         row.get('traceWidthRule') == ('HIGH_CURRENT=13;SUPPLY=11;RETURN=11;CONTROL=9;SIGNAL=9'
             if version == 12 else 'ALL_ROLES=9') and
         row.get('largestProtectedEmptyRectangleAreaMetricBasis') ==
             ('NOT_CLAIMED_NO_SAVED_V3_WHITESPACE_ORACLE' if version == 3 else
              'EXISTING_TEST_DRAWING_SPACE_SAMPLING_10_UNIT') and
         ((version == 3 and row.get('largestProtectedEmptyRectangleArea') is None) or
          (version >= 4 and integer(row.get('largestProtectedEmptyRectangleArea'), 0, (1 << 31) - 1))) and
         integer(row.get('clearanceEdgeRuleDrawingUnits'), 6, 6) and
         row.get('clearanceRuleBasis') == 'PAIRWISE_ROLE_WIDTH_ENVELOPE_PLUS_6_DRAWING_UNITS' and
         integer(row.get('clearanceMinimumCenterlineRule'), 15, 15) and
         row.get('selectedRoutePolicy') == 'P07_FULLER_TWO_LAYER' and row.get('routeAccepted') is True,
         'saved-profile physical policy/profile/route differs')
    for field in ['layoutGeometryValidation', 'twoLayerRulesValidation',
                  'productionFiniteWidthClearance', 'independentReferenceClearance',
                  'independentP08Partition', 'layeredCenterline']:
        need(row.get(field) == 'PASS', 'missing native independent geometry witness: ' + field)
    need(row.get('normalAdmission') == row.get('nativePrivacy') == 'NOT CLAIMED',
         'observational saved-profile report overstates admission/privacy')
    need(row.get('fixtureIdentity') == 'RB56_CONTROL/' + cohort['id'] + '/' + seed +
         '/placement-' + str(version), 'saved-profile fixture identity differs')
    need(canonical_signed_long(row.get('placementSeed')) and
         canonical_signed_long(row.get('routingSeed')),
         'named physical random streams are not exact signed-long decimal strings')
    need(type(row.get('topology')) is str and row['topology'] and
         row.get('boardId') == 'RB56_CONTROL_BOARD' and
         type(row.get('logicalInventoryCanonical')) is str and
         0 < len(row['logicalInventoryCanonical']) <= 1024 * 1024,
         'missing complete logical plan/fixture declaration')
    for field in ['layoutGeometryFingerprint', 'traceGeometryFingerprint', 'p08Canonical',
                  'placementCanonical', 'policyStatisticsCanonical']:
        need(type(row.get(field)) is str and 0 < len(row[field]) <= 4 * 1024 * 1024,
             'missing bounded physical field: ' + field)
    for key in ['layoutGeometrySha256', 'normalizedPhysicalSha256', 'traceGeometrySha256',
                'p08CanonicalSha256', 'placementCanonicalSha256']:
        need(type(row.get(key)) is str and re.fullmatch('[0-9a-f]{64}', row[key]) is not None,
             'invalid physical fingerprint field: ' + key)
    normalized_geometry(row)
    need(sha_text(row['traceGeometryFingerprint']) == row['traceGeometrySha256'] and
         sha_text(row['p08Canonical']) == row['p08CanonicalSha256'] and
         sha_text(row['placementCanonical']) == row['placementCanonicalSha256'],
         'geometry/route/P08/placement canonical digest differs')

    outline = row.get('boardOutline')
    tray = row.get('partsTray')
    rectangles_are_valid(outline, 'board outline')
    rectangles_are_valid(tray, 'parts tray')
    area = outline['width'] * outline['height']
    need(integer(row.get('boardArea'), 1, (1 << 63) - 1) and row['boardArea'] == area and
         integer(row.get('layoutWidth'), outline['width'], (1 << 31) - 1) and
         integer(row.get('layoutHeight'), outline['height'], (1 << 31) - 1),
         'board area or layout dimensions differ from its declared outline')
    need(type(row.get('boardUtilization')) in [int, float] and math.isfinite(row['boardUtilization']) and
         0 < row['boardUtilization'] <= 1, 'invalid finite board utilization')

    components = row.get('componentMappings')
    expected_packages = row.get('expectedPackages')
    expected_pads = row.get('expectedPads')
    need(row.get('censusBasis') == 'DECLARED_PLAN_INVENTORY' and
         integer(expected_packages, 1, 60) and integer(expected_pads, 1, 300) and
         row.get('declaredComponentCount') == expected_packages and
         row.get('declaredPadCount') == expected_pads and
         type(components) is list and len(components) == expected_packages and
         len({item.get('componentId') for item in components}) == expected_packages,
         'declared-plan package census is not independently complete')
    if cohort['cohort'] == 'representative':
        need(catalog_row['literalFixtureCensus']['componentCount'] == expected_packages and
             catalog_row['literalFixtureCensus']['padCount'] == expected_pads,
             'representative native census differs from frozen source literals')
    else:
        need(catalog_row['literalFixtureCensus']['status'] ==
             'NOT_DECLARED_FOR_HOLDOUT_BY_EITHER_SOURCE',
             'holdout fixture census was not left plan-derived')
    component_ids, terminal_ids = set(), set()
    package_census = collections.Counter()
    component_semantics, component_geometry = [], {}
    internal_link_count = 0
    internal_connected_link_count = 0
    for component in components:
        need(type(component) is dict and type(component.get('componentId')) is str and
             component['componentId'] and type(component.get('physicalPackage')) is str and
             type(component.get('placementFingerprint')) is str,
             'incomplete component mapping record')
        component_id = component['componentId']
        component_ids.add(component_id)
        package_census[component['physicalPackage']] += 1
        for field in ['bodyBounds', 'keepOutBounds', 'routingCourtyardBounds']:
            rectangles_are_valid(component.get(field), field)
        terminals = component.get('terminals')
        need(type(terminals) is list and terminals, 'component has no physical terminal map')
        terminal_rows = []
        for pad in terminals:
            need(type(pad) is dict and type(pad.get('padId')) is str and
                 type(pad.get('terminalId')) is str and type(pad.get('netId')) is str and
                 pad.get('netId') == pad.get('declaredNetId') and
                 pad.get('attachment') == 'PLATED_THROUGH_HOLE' and
                 pad.get('padId') == component_id + '.' + pad.get('terminalId') and
                 all(integer(pad.get(key), -(1 << 31), (1 << 31) - 1)
                     for key in ['x', 'y', 'escapeDx', 'escapeDy', 'escapeLength']) and
                 pad['escapeLength'] >= 0,
                 'component terminal identity or attachment differs')
            need(pad['padId'] not in terminal_ids, 'duplicate physical terminal identity')
            terminal_ids.add(pad['padId'])
            terminal_rows.append([pad['padId'], pad['terminalId'], pad['netId'], pad['attachment']])
        component_semantics.append([
            component_id, component.get('type'), component.get('kind'), component['physicalPackage'],
            component.get('value'), component.get('channel'), component.get('inputId'),
            component.get('boardType'), terminal_rows,
        ])
        component_geometry[component_id] = sha_text(component['placementFingerprint'])
        internal_link_count += len(re.findall(r'internal:[^|;]+=[^|;]+=(?:true|false);',
                                              component['placementFingerprint']))
        internal_connected_link_count += len(re.findall(
            r'internal:[^|;]+=[^|;]+=true;', component['placementFingerprint']))
    need(len(terminal_ids) == expected_pads and row.get('packageCensus') == dict(sorted(package_census.items())) and
         sum(package_census.values()) == expected_packages,
         'component/package/terminal census differs')
    nets = row.get('declaredNets')
    roles = row.get('declaredNetRoutingRoles')
    need(type(nets) is list and nets == sorted(set(nets)) and
         type(roles) is dict and list(roles.keys()) == sorted(roles) and
         set(roles) == set(nets) and set(roles.values()) <= set(ROLE_WIDTHS),
         'declared net/role inventory differs')
    need(all(pad_id.rsplit('.', 1)[0] in component_ids for pad_id in terminal_ids),
         'terminal does not belong to a declared component')

    traces = parse_trace_fingerprint(row['traceGeometryFingerprint'])
    native_width_census = row.get('traceWidthCensusByRole')
    width_census = validate_trace_roles(traces, roles, version, native_width_census)
    segments, normalized_segments, trace_layer_counts = trace_segment_inventory(traces)
    need(integer(row.get('tracePolylineCount'), 0, len(traces)) and
         row.get('tracePolylineCount') == len(traces) and
         integer(row.get('traceSegmentCount'), 0, len(segments)) and
         row.get('traceSegmentCount') == len(segments) and
         row.get('traceCountsByLayer') == trace_layer_counts,
         'trace polyline/segment/layer census differs')
    pair_count, width_pair_counts, maximum_clearance = independent_width_clearance(traces, segments)
    need(integer(row.get('referenceClearancePairs'), 0, 1 << 63) and
         row.get('referenceClearancePairs') == pair_count,
         'independent all-pairs finite-width census differs')
    expected_potential = len(segments) * (len(segments) - 1) // 2
    need(integer(row.get('productionClearancePotentialPairs'), 0, expected_potential) and
         row.get('productionClearancePotentialPairs') == expected_potential and
         integer(row.get('productionClearanceExactPairs'), 0, pair_count),
         'production clearance counters differ from bounded segment inventory')

    holes = row.get('holes')
    need(type(holes) is list and len(holes) <= expected_pads + 48 + 96 and
         len({hole.get('id') for hole in holes}) == len(holes), 'hole inventory is missing or duplicated')
    hole_counts = collections.Counter()
    hole_ids = {}
    for hole in holes:
        need(type(hole) is dict and type(hole.get('id')) is str and hole['id'] and
             hole.get('kind') in {'VIA', 'PLATED', 'NON_PLATED'} and
             (hole.get('netId') is None or hole.get('netId') in roles) and
             integer(hole.get('x'), -(1 << 31), (1 << 31) - 1) and
             integer(hole.get('y'), -(1 << 31), (1 << 31) - 1) and
             integer(hole.get('drillRadius'), 0, 100000) and
             integer(hole.get('landRadius'), 0, 100000) and
             type(hole.get('fingerprint')) is str and hole['fingerprint'],
             'invalid plated/nonplated/via hole witness')
        hole_counts[hole['kind']] += 1
        hole_ids[hole['id']] = hole['fingerprint']
    hole_map = dict(sorted(hole_counts.items()))
    need(row.get('holeCountsByKind') == hole_map and
         integer(row.get('viaCount'), 0, 48) and row.get('viaCount') == hole_counts['VIA'] and
         integer(row.get('platedHoleCount'), 0, expected_pads + 48 + 96) and
         row.get('platedHoleCount') == hole_counts['PLATED'] and
         integer(row.get('nonPlatedHoleCount'), 0, expected_pads + 48 + 96) and
         row.get('nonPlatedHoleCount') == hole_counts['NON_PLATED'],
         'hole kind counts or inherited via limit differ')
    lands = 2 * (expected_pads + hole_counts['VIA'] + hole_counts['PLATED'])
    expected_p08 = {
        'p08SourceSegmentCount': normalized_segments,
        'p08SourceLandCount': lands,
        'p08SegmentPairs': normalized_segments * (normalized_segments - 1) // 2,
        'p08SegmentLandPairs': normalized_segments * lands,
        'p08LandPairs': lands * (lands - 1) // 2,
        'p08OrderedPadPairs': expected_pads * expected_pads,
        'layeredOrderedPadPairs': expected_pads * expected_pads,
        'layeredPlatedPadBarrels': expected_pads,
        'layeredViaBarrels': hole_counts['VIA'],
        'layeredPlatedHoleBarrels': hole_counts['PLATED'],
    }
    for key, expected in expected_p08.items():
        need(integer(row.get(key), 0, 1 << 63) and row[key] == expected,
             'independent P08/layered raster counter differs: ' + key)
    raster_points, unit_steps = independent_layered_counts(components, traces, holes)
    need(integer(row.get('layeredRasterPoints'), 1, 1 << 63) and
         row.get('layeredRasterPoints') == raster_points and
         integer(row.get('layeredTraceUnitSteps'), 0, 1 << 63) and
         row.get('layeredTraceUnitSteps') == unit_steps and
         integer(row.get('layeredPackageInternalLinks'), 0, 1 << 63) and
         row.get('layeredPackageInternalLinks') == internal_connected_link_count and
         internal_connected_link_count <= internal_link_count,
         'independent layered centerline point/step/package-link census differs')

    hash_status = 'NO_EXACT_LITERAL_SOURCE' if catalog_row.get('oldHash') is None else (
        'BASELINE_OLD_LITERAL_MATCH' if side == 'baseline' and
        row['normalizedPhysicalSha256'] == catalog_row['oldHash'] else
        'CURRENT_EXACT_GEOMETRY_MATCH_NOT_ADOPTED' if
        row['normalizedPhysicalSha256'] == catalog_row['oldHash'] else
        'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW')
    if side == 'baseline' and catalog_row.get('oldHash') is not None:
        need(row['normalizedPhysicalSha256'] == catalog_row['oldHash'],
             'layout22 baseline did not reproduce its source-derived old physical hash')
    area_checks = old_bound_checks(row, catalog_row, source_texts, arrays_by_source,
                                   side, bound_failures)
    other_checks = check_other_source_bounds(row, catalog_row)
    component_semantic_hash = sha_text(json.dumps(component_semantics, separators=(',', ':'),
                                                   ensure_ascii=True, allow_nan=False))
    return {
        'cohortId': cohort['id'], 'caseOrdinal': case_ordinal,
        'placementInterpretationVersion': version, 'rootSeed': seed,
        'runId': row['runId'], 'frameId': row['frameId'],
        'layoutVersion': layout_version, 'dependencyEpoch': dependency,
        'placementSeed': row.get('placementSeed'), 'routingSeed': row.get('routingSeed'),
        'topology': row['topology'], 'fixtureIdentity': row['fixtureIdentity'],
        'logicalInventorySha256': sha_text(row['logicalInventoryCanonical']),
        'componentTerminalIdentitySha256': component_semantic_hash,
        'declaredNetsSha256': sha_text(json.dumps(nets, separators=(',', ':'))),
        'routingRolesSha256': sha_text(json.dumps(roles, sort_keys=True, separators=(',', ':'))),
        'componentGeometryFingerprints': component_geometry,
        'holeFingerprints': hole_ids,
        'expectedPackages': expected_packages, 'expectedPads': expected_pads,
        'packageCensus': dict(sorted(package_census.items())), 'declaredNets': nets,
        'declaredNetRoutingRoles': roles,
        'layoutGeometrySha256': row['layoutGeometrySha256'],
        'normalizedPhysicalSha256': row['normalizedPhysicalSha256'],
        'oldHash': catalog_row.get('oldHash'), 'oldHashStatus': hash_status,
        'placementCanonicalSha256': row['placementCanonicalSha256'],
        'traceGeometrySha256': row['traceGeometrySha256'],
        'p08CanonicalSha256': row['p08CanonicalSha256'],
        'policyStatisticsSha256': sha_text(row['policyStatisticsCanonical']),
        'boardArea': area, 'boardOutline': outline,
        'largestProtectedEmptyRectangleArea': row.get('largestProtectedEmptyRectangleArea'),
        'largestProtectedEmptyRectangleAreaMetricBasis': row.get('largestProtectedEmptyRectangleAreaMetricBasis'),
        'tracePolylineCount': len(traces), 'traceSegmentCount': len(segments),
        'normalizedTraceSegmentCount': normalized_segments,
        'traceCountsByLayer': trace_layer_counts,
        'traceWidthCensusByRole': dict(sorted(width_census.items())),
        'referenceClearancePairs': pair_count,
        'widthPairSegmentCensus': width_pair_counts,
        'maximumObservedClearanceRule': maximum_clearance,
        'viaCount': hole_counts['VIA'], 'platedHoleCount': hole_counts['PLATED'],
        'nonPlatedHoleCount': hole_counts['NON_PLATED'], 'holeCountsByKind': hole_map,
        'p08IndependentCounts': expected_p08,
        'layeredRasterPoints': row['layeredRasterPoints'],
        'layeredTraceUnitSteps': row['layeredTraceUnitSteps'],
        'layeredPackageInternalLinks': row['layeredPackageInternalLinks'],
        'selectedPlacementAttempt': row['selectedPlacementAttempt'],
        'boardAreaBounds': area_checks, 'otherSourceBounds': other_checks,
        'protectedVoidStatus': 'NOT_CLAIMED_NO_SAVED_V3_ORACLE' if version == 3 else (
            'BASELINE_SAMPLED_AND_SAVED_SOURCE_BOUNDS_ENFORCED' if side == 'baseline' else
            'CURRENT_SAMPLED_COMPARATIVE_BOUNDS_ENFORCED_EXACT_CHANGES_REQUIRE_REVIEW'),
        'nativeDiagnosticElapsedMs': row['nativeDiagnosticElapsedMs'],
    }


def whitespace_auxiliary(value, row, version, seed):
    need(type(value) is dict and value.get('seed') == seed and value.get('version') == version and
         value.get('scope') == ('GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;'
                                'FREE_IS_NOT_PACKABLE_PROOF') and value.get('gridUnits') == 10 and
         value.get('parts') == row.get('expectedPackages'),
         'saved-profile auxiliary whitespace observation identity differs')
    sampled = value.get('sampledCells')
    occupied = value.get('packageAccessOccupiedCells')
    free = value.get('protectedFreeCells')
    need(integer(sampled, 1, 1 << 31) and integer(occupied, 0, sampled) and
         integer(free, 0, sampled), 'invalid sampled protected-whitespace cell counts')
    fraction = value.get('protectedFreeFraction')
    need(type(fraction) in [int, float] and math.isfinite(fraction) and
         abs(fraction - free / sampled) <= 1e-12,
         'protected whitespace fraction differs from sampled free-cell census')
    rectangle = value.get('largestProtectedEmptyRectangle')
    need(type(rectangle) is dict and all(integer(rectangle.get(key), -(1 << 31), (1 << 31) - 1)
         for key in ['x', 'y']) and all(integer(rectangle.get(key), 0, 1 << 31)
         for key in ['width', 'height', 'area']) and
         rectangle['area'] == rectangle['width'] * rectangle['height'] and
         rectangle['area'] == row.get('largestProtectedEmptyRectangleArea'),
         'sampled protected-empty rectangle differs from the case metric')
    return {
        'gridUnits': 10, 'sampledCells': sampled,
        'packageAccessOccupiedCells': occupied, 'protectedFreeCells': free,
        'protectedFreeFraction': fraction,
        'largestProtectedEmptyRectangle': rectangle,
        'scope': value['scope'],
    }


def extract_native(lines, cohort, layout_version, side, console=False):
    events = []
    auxiliary = []
    cleanup_text = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
    cleanup_indices = [index for index, line in enumerate(lines) if line == cleanup_text]
    frame_indices = [index for index, line in enumerate(lines) if line.startswith(FRAME)]
    footer_indices = [index for index, line in enumerate(lines) if line.startswith(FOOTER)]
    need(len(frame_indices) == 1 and len(footer_indices) == 1,
         side + ' native output lacks unique frame/footer boundaries')
    frame_index, footer_index = frame_indices[0], footer_indices[0]
    need(frame_index < footer_index, side + ' native footer precedes its frame')
    if console:
        validate_console_preamble_lines(lines[:frame_index])
        need(len(cleanup_indices) == 1 and cleanup_indices[0] > footer_index,
             side + ' console lacks the unique maintained cleanup marker after its native footer')
    else:
        need(frame_index == 0, side + ' receipt contains a preamble before the native frame')
    for index, line in enumerate(lines):
        if console and (index < frame_index or index > footer_index):
            continue
        if line.startswith(FRAME):
            events.append(('frame', strict_json(line[len(FRAME):]), index))
        elif line.startswith(CASE):
            events.append(('case', strict_json(line[len(CASE):]), index))
        elif line.startswith(SUMMARY):
            events.append(('summary', strict_json(line[len(SUMMARY):]), index))
        elif line.startswith('PCB_WHITESPACE_CASE '):
            auxiliary.append((strict_json(line[len('PCB_WHITESPACE_CASE '):]), index))
        elif line.startswith(FOOTER):
            events.append(('footer', line, index))
        elif line in {
                'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.',
                cleanup_text}:
            raise ValueError(side + ' wrapper-only marker appears inside the native body')
        elif not line.strip():
            raise ValueError(side + ' native output contains an unexpected blank line')
        else:
            raise ValueError(side + ' native output contains unexpected content')
    expected_order = cohort_case_order(cohort)
    expected_kinds = ['frame']
    for version, _, _ in expected_order:
        if version >= 4:
            expected_kinds.append('whitespace')
        expected_kinds.append('case')
    expected_kinds.extend(['summary', 'footer'])
    actual_kinds = []
    events_by_index = {index: (kind, value) for kind, value, index in events}
    auxiliary_by_index = {index: ('whitespace', value) for value, index in auxiliary}
    merged_by_index = {**events_by_index, **auxiliary_by_index}
    for index in sorted(merged_by_index):
        actual_kinds.append(merged_by_index[index][0])
    need(actual_kinds == expected_kinds, side + ' frame/case/auxiliary/footer framing differs')
    frame, summary = events[0][1], events[-2][1]
    case_events = [(value, index) for kind, value, index in events if kind == 'case']
    aux_events = [(value, index) for value, index in auxiliary]
    dependency = 'tsj-generation-dependencies-v' + str(layout_version + 1)
    run_id = frame.get('runId')
    need(uuid_text(run_id) and frame.get('protocol') == PROTOCOL and
         frame.get('cohortId') == cohort['id'] and integer(frame.get('caseCount'), 12, 12) and
         integer(frame.get('caseOrdinalBase'), 1, 1) and
         frame.get('caseOrder') == 'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER' and
         frame.get('placementVersions') == cohort['versions'] and frame.get('rootSeeds') == cohort['seeds'] and
         frame.get('layoutVersion') == layout_version and frame.get('dependencyEpoch') == dependency and
         frame.get('scope') == SCOPE and frame.get('layoutQualification') is False and
         frame.get('adopted') is False and frame.get('adoption') is False and
         frame.get('baselineExpected') == 'EXTERNAL_SAVED_PROFILE_CATALOG',
         side + ' saved-profile native frame differs')
    need(summary.get('protocol') == PROTOCOL and summary.get('runId') == run_id and
         summary.get('cohortId') == cohort['id'] and integer(summary.get('cases'), 12, 12) and
         integer(summary.get('caseCount'), 12, 12) and
         summary.get('caseOrder') == 'PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER' and
         summary.get('placementVersions') == cohort['versions'] and summary.get('rootSeeds') == cohort['seeds'] and
         integer(summary.get('assertions'), 1, 10_000_000) and
         integer(summary.get('nativeDiagnosticElapsedMs'), 0, 180000) and
         summary.get('timingScope') == 'NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING' and
         summary.get('layoutVersion') == layout_version and summary.get('dependencyEpoch') == dependency and
         summary.get('scope') == SCOPE and summary.get('layoutQualification') is False and
         summary.get('adopted') is False and summary.get('adoption') is False and
         summary.get('baselineExpected') == 'EXTERNAL_SAVED_PROFILE_CATALOG',
         side + ' saved-profile native summary differs')
    whitespace_by_case = {}
    for item, _ in aux_events:
        key = (item.get('version'), item.get('seed'))
        need(key not in whitespace_by_case, side + ' duplicates a whitespace auxiliary')
        whitespace_by_case[key] = item
    observations = []
    frame_ids = set()
    for (version, seed, ordinal), (row, _) in zip(expected_order, case_events):
        need(row.get('runId') == run_id and uuid_text(row.get('frameId')) and
             row.get('layoutVersion') == layout_version and row.get('dependencyEpoch') == dependency and
             row.get('cohortId') == cohort['id'], side + ' case invocation frame differs')
        need(row['frameId'] not in frame_ids, side + ' duplicates a native per-case frame ID')
        frame_ids.add(row['frameId'])
        if version >= 4:
            key = (version, seed)
            need(key in whitespace_by_case, side + ' case is missing its source whitespace auxiliary')
            whitespace_auxiliary(whitespace_by_case.pop(key), row, version, seed)
        else:
            need(row.get('largestProtectedEmptyRectangleArea') is None,
                 side + ' v3 claims a whitespace measurement absent from its saved oracle')
        observations.append(row)
    need(not whitespace_by_case, side + ' has an unpaired whitespace auxiliary')
    expected_footer = (FOOTER + 'cohortId=' + cohort['id'] + ' assertions=' + str(summary['assertions']) +
        ' cases=12 layout=' + str(layout_version) + ' dependency=' + dependency +
        ' layoutQualification=false adoption=false scope=' + SCOPE)
    footer_line = events[-1][1]
    need(footer_line == expected_footer, side + ' terminal native pass footer differs')
    native_end = events[-1][2]
    native_lines = lines[frame_index:native_end + 1]
    if console:
        suffix = [line.strip() for line in lines[native_end + 1:] if line.strip()]
        need(suffix == [
            'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.',
            'CLEANUP: current JVM contract classes and task-owned scratch removed.',
        ], side + ' console lacks the exact maintained focused-pass/cleanup terminal suffix')
    else:
        need(native_end == len(lines) - 1, side + ' receipt has trailing data after native pass')
    need(sum(integer(row.get('nativeDiagnosticElapsedMs'), 0, 180000) and
             row['nativeDiagnosticElapsedMs'] for row in observations) <= summary['nativeDiagnosticElapsedMs'],
         side + ' case timing exceeds the summary observation interval')
    return {'frame': frame, 'cases': observations, 'summary': summary,
            'footer': footer_line, 'nativeLines': native_lines,
            'auxiliaryCount': len(aux_events)}


def load_source_pins(path):
    raw = read_bounded(path, MAX_PIN_BYTES, 'sealed verifier source-pin snapshot')
    rows = strict_json(raw.decode('utf-8-sig'))
    need(type(rows) is list and len(rows) == 1059,
         'sealed source-pin snapshot must contain exactly 1059 consumed inputs')
    result = {}
    for row in rows:
        need(type(row) is dict and type(row.get('path')) is str and
             type(row.get('sha256')) is str, 'invalid source-pin record')
        key = row['path'].replace('\\', '/')
        need(key not in result and not key.startswith('/') and
             re.match(r'^[A-Za-z]:', key) is None and '..' not in key.split('/') and
             re.fullmatch(r'[0-9a-f]{64}', row['sha256']) is not None,
             'invalid or duplicate source-pin path/hash')
        result[key] = row['sha256']
    return result, raw


def verify_pin_file_binding(raw, expected_sha, expected_count):
    need(type(expected_sha) is str and re.fullmatch(r'[0-9a-f]{64}', expected_sha) is not None and
         integer(expected_count, 1059, 1059) and sha_bytes(raw) == expected_sha,
         'launcher input pin digest/count does not bind the exact sealed source bytes')


def validate_pin_delta(before, after):
    need(len(EQUAL_INPUTS) == len(set(EQUAL_INPUTS)),
         'unchanged input allowlist contains duplicate paths')
    need(set(EXPECTED_EQUAL_INPUT_ORACLE_HASHES).issubset(EQUAL_INPUTS) and
         all(EQUAL_INPUTS.count(path) == 1 for path in EXPECTED_EQUAL_INPUT_ORACLE_HASHES) and
         MISLOCATED_ORACLE_PATHS.isdisjoint(EQUAL_INPUTS),
         'unchanged oracle allowlist membership or exact source paths differ')
    baseline_only = sorted(set(before) - set(after))
    current_only = sorted(set(after) - set(before))
    need(baseline_only == REMOVED_TESTS and current_only == ADDED_TESTS,
         'baseline/current pin path transition differs from exact retired/added tests')
    changed = sorted(key for key in set(before) & set(after) if before[key] != after[key])
    need(changed == COMMON_CHANGED,
         'native source inputs changed beyond the exact reviewed baseline/current delta')
    need(sorted(key for key in changed if key.startswith('src/')) == PRODUCTION_CHANGES,
         'production source changes differ from the four declared routing/epoch files')
    for key in EQUAL_INPUTS:
        need(key in before and key in after and before[key] == after[key],
             'independent diagnostic/oracle/tool input changed: ' + key)
    for key, expected_sha256 in EXPECTED_EQUAL_INPUT_ORACLE_HASHES.items():
        need(before.get(key) == expected_sha256 and after.get(key) == expected_sha256,
             'unchanged oracle bytes differ from the two sealed v2 pin snapshots: ' + key)
    jars_before = {key: value for key, value in before.items() if key.endswith('.jar')}
    jars_after = {key: value for key, value in after.items() if key.endswith('.jar')}
    need(jars_before == jars_after, 'JDK/GWT jar input drifted across the saved-profile comparison')
    need(set(before) - set(REMOVED_TESTS) == set(after) - set(ADDED_TESTS),
         'native source snapshots have an unreviewed path-set difference')
    return changed, baseline_only, current_only, jars_before


def source_snapshot_root(normalizer_path, side):
    path = Path(normalizer_path).resolve(strict=True)
    need(path.is_file() and path.name == 'PcbCompactnessContractTest.java',
         side + ' normalizer path is not the pinned compactness helper')
    root = path.parents[2]
    need(path.relative_to(root).as_posix() ==
         'tests/contracts/PcbCompactnessContractTest.java',
         side + ' normalizer does not have the expected snapshot-relative path')
    return root


def compare_source_pins(baseline, current):
    before, before_raw = load_source_pins(baseline['pins'])
    after, after_raw = load_source_pins(current['pins'])
    changed, baseline_only, current_only, jars_before = validate_pin_delta(before, after)
    need(re.fullmatch(r'[0-9a-f]{64}', BASELINE_PIN_FILE_SHA256) is not None and
         sha_bytes(before_raw) == BASELINE_PIN_FILE_SHA256 and
         re.fullmatch(r'[0-9a-f]{64}', CURRENT_PIN_FILE_SHA256) is not None and
         sha_bytes(after_raw) == CURRENT_PIN_FILE_SHA256,
         'sealed source-pin snapshot digest differs from the frozen run inputs')
    baseline_root = source_snapshot_root(baseline['normalizer'], 'baseline')
    current_root = source_snapshot_root(current['normalizer'], 'current')
    diagnostic_source = diagnostic_source_boundary(
        baseline_root, current_root, before, after)
    need(diagnostic_source.get('baselineRawSha256') == P07_DIAGNOSTIC_BASELINE_SHA256 and
         diagnostic_source.get('currentRawSha256') == P07_DIAGNOSTIC_CURRENT_SHA256 and
         diagnostic_source.get('sameLogicAfterSixVersionLiteralAdapters') is True and
         diagnostic_source.get('sourceLogicMutationCanary') == 'PASS_REJECTED',
         'P07 diagnostic source changed beyond its sealed six-literal epoch adapter')
    helper_key = 'tests/contracts/PcbCompactnessContractTest.java'
    old_helper_raw = read_bounded(baseline['normalizer'], MAX_NORMALIZER_BYTES,
                                  'baseline physical normalizer source')
    new_helper_raw = read_bounded(current['normalizer'], MAX_NORMALIZER_BYTES,
                                  'current physical normalizer source')
    need(len(old_helper_raw) > 0 and len(new_helper_raw) > 0,
         'invalid bounded normalizer source')
    old_helper = normalizer_boundary(baseline['normalizer'], before[helper_key], 22)
    new_helper = normalizer_boundary(current['normalizer'], after[helper_key], 24)
    need(old_helper['epochNeutralCalledBlockSha256'] == new_helper['epochNeutralCalledBlockSha256'],
         'physical normalizer helper changed beyond its four epoch-only literals')
    new_text = new_helper_raw.decode('utf-8-sig')
    need(new_text.count('return normalized;') == 1,
         'normalizer self-canary target differs')
    mutated = new_text.replace('return normalized;', 'return fingerprint;')
    need(sha_text(epoch_neutral_normalizer(mutated, 24)) !=
         new_helper['epochNeutralCalledBlockSha256'],
         'normalizer logic mutation escaped the epoch-neutral comparison canary')
    need(new_text.count('CURRENT_VERSION != 24') == 1,
         'current normalizer version guard is not the frozen layout24 boundary')
    stale_epoch_text = new_text.replace('CURRENT_VERSION != 24', 'CURRENT_VERSION != 23')
    try:
        epoch_neutral_normalizer(stale_epoch_text, 24)
    except ValueError:
        wrong_epoch_rejected = True
    else:
        wrong_epoch_rejected = False
    need(wrong_epoch_rejected, 'normalizer wrong-epoch self-canary was accepted')
    report = {
        'baselinePinCount': len(before), 'currentPinCount': len(after),
        'baselinePinsSha256': sha_bytes(before_raw), 'currentPinsSha256': sha_bytes(after_raw),
        'commonChangedPaths': changed, 'baselineRetiredPaths': baseline_only,
        'currentAddedPaths': current_only, 'equalInputsCount': len(EQUAL_INPUTS),
        'unchangedJarCount': len(jars_before),
        'normalizer': {'baseline': old_helper, 'current': new_helper,
                       'sameCalledLogicAfterFourEpochLiterals': True,
                       'mutationCanaryRejected': True, 'wrongEpochCanaryRejected': True},
        'diagnosticSourceBoundary': diagnostic_source,
    }
    return report, before, baseline_root


def compare_logical_cases(baseline_cases, current_cases, cohort):
    need(len(baseline_cases) == len(current_cases) == 12,
         'baseline/current native pair is not a complete 12-case cohort')
    comparisons = []
    for (version, seed, ordinal), old, new in zip(
            cohort_case_order(cohort), baseline_cases, current_cases):
        need(old['caseOrdinal'] == new['caseOrdinal'] == ordinal and
             old['placementInterpretationVersion'] == new['placementInterpretationVersion'] == version and
             old['rootSeed'] == new['rootSeed'] == seed and
             old['fixtureIdentity'] == new['fixtureIdentity'] and
             old['topology'] == new['topology'] and
             old['logicalInventorySha256'] == new['logicalInventorySha256'] and
             old['componentTerminalIdentitySha256'] == new['componentTerminalIdentitySha256'] and
             old['expectedPackages'] == new['expectedPackages'] and
             old['expectedPads'] == new['expectedPads'] and
             old['packageCensus'] == new['packageCensus'] and
             old['declaredNets'] == new['declaredNets'] and
             old['routingRolesSha256'] == new['routingRolesSha256'] and
             old['declaredNetRoutingRoles'] == new['declaredNetRoutingRoles'] and
             old['placementSeed'] == new['placementSeed'] and
             old['routingSeed'] == new['routingSeed'],
             'baseline/current logical plan, component/terminal identity, or named seed streams differ')
        comparisons.append({
            'caseOrdinal': ordinal, 'placementInterpretationVersion': version,
            'rootSeed': seed, 'logicalPlanAndIdentityStable': True,
            'placementSeed': old['placementSeed'], 'routingSeed': old['routingSeed'],
            'baselineNormalizedPhysicalSha256': old['normalizedPhysicalSha256'],
            'currentNormalizedPhysicalSha256': new['normalizedPhysicalSha256'],
            'baselineOldSourceHashStatus': old['oldHashStatus'],
            'currentOldSourceHashStatus': new['oldHashStatus'],
            'baselineBoardArea': old['boardArea'], 'currentBoardArea': new['boardArea'],
            'baselineAreaSourceBounds': old['boardAreaBounds'],
            'currentAreaSourceBounds': new['boardAreaBounds'],
            'baselineOtherSourceBounds': old['otherSourceBounds'],
            'currentOtherSourceBounds': new['otherSourceBounds'],
            'baselineTraceSegments': old['traceSegmentCount'],
            'currentTraceSegments': new['traceSegmentCount'],
            'baselineViaCount': old['viaCount'], 'currentViaCount': new['viaCount'],
            'baselineProtectedEmptyRectangleArea': old.get('largestProtectedEmptyRectangleArea'),
            'currentProtectedEmptyRectangleArea': new.get('largestProtectedEmptyRectangleArea'),
            'physicalIdentityChanged': old['normalizedPhysicalSha256'] != new['normalizedPhysicalSha256'],
        })
    return comparisons


def read_side(side_paths, cohort, side, layout_version, catalog_rows, source_texts,
              arrays_by_source, retain_raw=False, bound_failures=None):
    cohort_tag = cohort['id'].removeprefix('SAVED_').lower().replace('_', '-')
    console_name = side_paths['console'].name
    need(re.fullmatch(r'saved-profile-' + re.escape(side) + '-' +
         re.escape(cohort_tag) + r'-\d+-console\.txt', console_name) is not None,
         side + ' console filename does not identify the selected saved-profile cohort')
    receipt_raw = read_bounded(side_paths['receipt'], MAX_RECEIPT_BYTES, side + ' native receipt')
    console_raw = read_bounded(side_paths['console'], MAX_CONSOLE_BYTES, side + ' native stdout console')
    console_preamble_raw = validate_console_preamble_bytes(console_raw)
    receipt_lines = receipt_raw.decode('utf-8-sig').splitlines()
    console_lines = console_raw.decode('utf-8-sig').splitlines()
    observation = extract_native(receipt_lines, cohort, layout_version, side, console=False)
    console_observation = extract_native(console_lines, cohort, layout_version, side, console=True)
    need(console_observation['nativeLines'] == observation['nativeLines'] and
         console_lines.count('CLEANUP: current JVM contract classes and task-owned scratch removed.') == 1,
         side + ' receipt and raw native stdout differ or cleanup marker is missing/duplicated')
    need(len(observation['cases']) == 12, side + ' native observation is not a complete cohort')
    summarized_cases = []
    for version, seed, ordinal, row in [
            (version, seed, ordinal, row)
            for (version, seed, ordinal), row in zip(cohort_case_order(cohort), observation['cases'])]:
        catalog_row = catalog_rows[(cohort['id'], version, seed)]
        summarized_cases.append(validate_case(row, cohort, ordinal, version, seed, side,
            catalog_row, source_texts, arrays_by_source, layout_version,
            bound_failures))

    result_raw = read_bounded(side_paths['result'], MAX_RESULT_BYTES, side + ' launcher result')
    result = strict_json(result_raw.decode('utf-8-sig'))
    need(type(result) is dict and integer(result.get('exitCode'), 0, 0) and
         result.get('source') == side and result.get('cohortId') == cohort['id'] and
         result.get('suite') == 'P07GeometryEpochDiagnosticContractTest' and
         result.get('inputRevision') == (2 if side == 'baseline' else 3) and
         result.get('nativeChildLimitMs') == 180000 and result.get('outerWatchdogMs') == 600000 and
         result.get('terminationProven') is True and result.get('inputAudit') == 'PASS_BEFORE_AND_AFTER' and
         result.get('inputCount') == 1059 and
         type(result.get('processId')) is int and result['processId'] > 0 and
         type(result.get('processStartTicks')) is int and result['processStartTicks'] > 0 and
         type(result.get('elapsedSeconds')) in [int, float] and
         0 < result['elapsedSeconds'] <= 600 and
         result['elapsedSeconds'] * 1000 >= observation['summary']['nativeDiagnosticElapsedMs'] and
         integer(result.get('stdoutBytes'), 0, MAX_CONSOLE_BYTES) and
         result.get('stdoutBytes') == len(console_raw) and
         result.get('stdoutSha256') == sha_bytes(console_raw) and
         integer(result.get('stderrBytes'), 0, MAX_CONSOLE_BYTES) and
         type(result.get('stderrSha256')) is str and
         re.fullmatch(r'[0-9a-f]{64}', result['stderrSha256']) is not None,
         side + ' maintained child result/termination/input audit differs')
    stderr_path = side_paths['console'].with_name(console_name.replace('-console.txt', '-stderr.txt'))
    if result['stderrBytes'] == 0:
        need(result['stderrSha256'] == sha_bytes(b''), side + ' empty stderr digest differs')
        if stderr_path.exists():
            need(stderr_path.is_file() and stderr_path.stat().st_size == 0,
                 side + ' zero-byte stderr file differs from launcher result')
    else:
        stderr_raw = read_bounded(stderr_path, MAX_CONSOLE_BYTES, side + ' stderr')
        need(len(stderr_raw) == result['stderrBytes'] and sha_bytes(stderr_raw) == result['stderrSha256'],
             side + ' captured stderr differs from launcher result')
    pin_map, pin_raw = load_source_pins(side_paths['pins'])
    verify_pin_file_binding(pin_raw, result.get('inputPinsSha256'), result['inputCount'])
    need(len(pin_map) == result['inputCount'],
         side + ' maintained input audit does not bind the sealed source-pin snapshot')
    result_value = {
        'cases': summarized_cases,
        'summary': observation['summary'],
        'result': {
            'source': side, 'exitCode': result['exitCode'],
            'helperElapsedSeconds': result['elapsedSeconds'],
            'nativeChildLimitMs': result['nativeChildLimitMs'],
            'outerWatchdogMs': result['outerWatchdogMs'],
            'terminationProven': result['terminationProven'],
            'inputAudit': result['inputAudit'], 'inputCount': result['inputCount'],
            'inputRevision': result['inputRevision'],
            'inputPinsSha256': result['inputPinsSha256'],
            'processId': result['processId'], 'processStartTicks': result['processStartTicks'],
            'stdoutBytes': result['stdoutBytes'], 'stdoutSha256': result['stdoutSha256'],
            'stderrBytes': result['stderrBytes'], 'stderrSha256': result['stderrSha256'],
        },
        'receiptSha256': sha_bytes(receipt_raw), 'receiptBytes': len(receipt_raw),
        'consoleSha256': sha_bytes(console_raw), 'consoleBytes': len(console_raw),
        'consolePreambleSha256': sha_bytes(console_preamble_raw),
        'consolePreambleBytes': len(console_preamble_raw),
        'consolePreambleLineCount': len(HOST_PREFIX_LINES),
        'pinFileSha256': sha_bytes(pin_raw),
    }
    if retain_raw:
        result_value.update({'observation': observation, 'pinMap': pin_map, 'pinRaw': pin_raw,
            'receiptLines': receipt_lines, 'consoleLines': console_lines})
    return result_value


def compare_dynamic_saved10_areas(side_results, selected_cohorts, catalog_rows, side,
                                 bound_failures=None):
    lookup = {}
    for cohort_id, sides in side_results.items():
        for row in sides[side]['cases']:
            lookup[(cohort_id, row['placementInterpretationVersion'], row['rootSeed'])] = row
    comparisons = []
    for cohort in selected_cohorts:
        for row in side_results[cohort['id']][side]['cases']:
            version = row['placementInterpretationVersion']
            if version != 11:
                continue
            catalog_row = catalog_rows[(cohort['id'], 11, row['rootSeed'])]
            source_check = None
            for source_id, entry in catalog_row['sourceExpectations'].items():
                for check in entry.get('areaComparisons') if type(entry.get('areaComparisons')) is list else []:
                    if check.get('literal') == 'previousArea from same seed version10 result':
                        source_check = (source_id, check)
            if source_check is None:
                continue
            predecessor_cohort = cohort['id'] if cohort['cohort'] == 'holdout' else 'SAVED_9_10_REP'
            previous = lookup.get((predecessor_cohort, 10, row['rootSeed']))
            if previous is None:
                comparisons.append({'side': side, 'cohortId': cohort['id'],
                    'rootSeed': row['rootSeed'], 'sourceId': source_check[0],
                    'sourceLiteral': source_check[1]['literal'],
                    'baselineLiteralCeiling': source_check[1]['value'],
                    'sourceCondition': source_check[1]['sourceCondition'],
                    'actualSaved11Area': row['boardArea'],
                    'status': 'NOT_EVALUATED_FINITE_COHORT_NO_SAVED10_OBSERVATION'})
                continue
            dynamic_status = retained_metric_status(
                row['boardArea'], previous['boardArea'], '<=', side,
                'same-seed saved10 observed board area', bound_failures,
                bound_failure_context(row, source_check[0], source_check[1], side,
                                      'SAME_SIDE_SAVED11_TO_SAVED10_AREA'))
            comparisons.append({'side': side, 'cohortId': cohort['id'],
                'rootSeed': row['rootSeed'], 'sourceId': source_check[0],
                'sourceLiteral': source_check[1]['literal'],
                'baselineLiteralCeiling': source_check[1]['value'],
                'sourceCondition': source_check[1]['sourceCondition'],
                'saved10Area': previous['boardArea'], 'saved11Area': row['boardArea'],
                'operator': '<=', 'status': 'PASS_SAME_SIDE_SAVED10_OBSERVATION'
                    if dynamic_status == 'PASS' else dynamic_status})
    return comparisons


def reject_canary(action, label):
    try:
        action()
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError):
        return 1
    raise ValueError('negative canary accepted: ' + label)


def validate_collection_scope(collect_bound_failures, selected_cohort):
    if collect_bound_failures:
        need(selected_cohort is None,
             '--collect-bound-failures requires the complete six-cohort union')


def comparison_outcome(selected_cohort, bound_failures):
    if bound_failures:
        return 'FAIL_RETAINED_BOUNDS', 2
    return ('PASS_FINITE_COHORT_ONLY' if selected_cohort is not None else
            'PASS_COMPARISON_ONLY'), 0


def negative_canaries(catalog_raw, catalog_sha, source_texts, arrays,
                      catalog_rows, cohort, baseline_cohort, current_cohort):
    checks = 0
    for malformed in ['{"a":1,"a":2}', '{"x":NaN}']:
        checks += reject_canary(lambda malformed=malformed: strict_json(malformed),
                                'strict JSON duplicate/nonfinite')
    baseline_native = baseline_cohort['observation']
    checks += reject_canary(lambda: extract_native(
        baseline_cohort['receiptLines'][:-1], cohort, 22, 'baseline'),
        'truncated native receipt')
    mutated_summary = list(baseline_cohort['receiptLines'])
    summary_index = next(i for i, line in enumerate(mutated_summary) if line.startswith(SUMMARY))
    summary = strict_json(mutated_summary[summary_index][len(SUMMARY):])
    summary['cases'] = 11
    mutated_summary[summary_index] = SUMMARY + json.dumps(summary, separators=(',', ':'))
    checks += reject_canary(lambda: extract_native(mutated_summary, cohort, 22, 'baseline'),
                            'summary case count')
    broken_cleanup = list(current_cohort['consoleLines'])
    cleanup_index = next(i for i, line in enumerate(broken_cleanup)
                         if line == 'CLEANUP: current JVM contract classes and task-owned scratch removed.')
    broken_cleanup[cleanup_index] = 'CLEANUP: marker missing'
    checks += reject_canary(lambda: extract_native(broken_cleanup, cohort, 24, 'current', console=True),
                            'maintained cleanup marker')
    checks += reject_canary(lambda: extract_native(
        current_cohort['receiptLines'], cohort, 23, 'current'),
        'stale layout23 current frame rejected after layout24 migration')
    prefix = list(current_cohort['consoleLines'][:len(HOST_PREFIX_LINES)])
    need(prefix == HOST_PREFIX_LINES, 'actual host-prefix canary input differs from frozen prefix')
    unknown_prefix = list(prefix)
    unknown_prefix.insert(3, 'FAIL: injected unrecognized host-prefix line')
    checks += reject_canary(lambda: validate_console_preamble_lines(unknown_prefix),
                            'unknown/fake FAIL preamble line')
    missing_jdk = list(prefix)
    missing_jdk.remove('JDK: javac 1.8.0_502')
    checks += reject_canary(lambda: validate_console_preamble_lines(missing_jdk),
                            'missing required JDK preamble marker')
    missing_bridge = list(prefix)
    missing_bridge.remove(HOST_PREFIX_LINES[6])
    checks += reject_canary(lambda: validate_console_preamble_lines(missing_bridge),
                            'missing required native-bridge preamble marker')
    injected_case = list(current_cohort['consoleLines'])
    case_line = next(line for line in injected_case if line.startswith(CASE))
    summary_position = next(i for i, line in enumerate(injected_case) if line.startswith(SUMMARY))
    injected_case.insert(summary_position, case_line)
    checks += reject_canary(lambda: extract_native(injected_case, cohort, 24, 'current', console=True),
                            'injected native case frame')
    blank_native_body = list(current_cohort['consoleLines'])
    frame_position = next(i for i, line in enumerate(blank_native_body) if line.startswith(FRAME))
    blank_native_body.insert(frame_position + 1, '')
    checks += reject_canary(lambda: extract_native(blank_native_body, cohort, 24, 'current', console=True),
                            'blank line inside native body')
    mutated_catalog = bytearray(catalog_raw)
    mutated_catalog[len(mutated_catalog) // 2] ^= 1
    checks += reject_canary(lambda: validate_catalog_bytes(bytes(mutated_catalog), catalog_sha),
                            'frozen catalog byte digest')
    before = baseline_cohort['pinMap']
    after = current_cohort['pinMap']
    changed_equal = copy.deepcopy(after)
    key = EQUAL_INPUTS[0]
    changed_equal[key] = 'f' * 64 if after[key] != 'f' * 64 else 'e' * 64
    checks += reject_canary(lambda: validate_pin_delta(before, changed_equal),
                            'unexpected compiler input mutation')
    correct_placement_path = 'src/com/lushprojects/circuitjs1/client/Rb56Placement.java'
    correct_trace_rules_path = 'src/com/lushprojects/circuitjs1/client/PcbTraceRules.java'
    wrong_placement_path = 'tests/contracts/Rb56Placement.java'
    wrong_trace_rules_path = 'tests/contracts/PcbTraceRules.java'
    wrong_path_map = copy.deepcopy(after)
    wrong_path_map[wrong_placement_path] = wrong_path_map.pop(correct_placement_path)
    wrong_path_map[wrong_trace_rules_path] = wrong_path_map.pop(correct_trace_rules_path)
    checks += reject_canary(lambda: validate_pin_delta(before, wrong_path_map),
                            'mislocated unchanged placement oracle path')
    missing_path_map = copy.deepcopy(after)
    del missing_path_map[correct_trace_rules_path]
    checks += reject_canary(lambda: validate_pin_delta(before, missing_path_map),
                            'missing unchanged trace-rules oracle path')
    wrong_hash_before = copy.deepcopy(before)
    wrong_hash_after = copy.deepcopy(after)
    wrong_hash = '0' * 64 if before[correct_placement_path] != '0' * 64 else '1' * 64
    wrong_hash_before[correct_placement_path] = wrong_hash
    wrong_hash_after[correct_placement_path] = wrong_hash
    checks += reject_canary(lambda: validate_pin_delta(wrong_hash_before, wrong_hash_after),
                            'unchanged oracle path with unexpected sealed hash')
    valid_width = parse_trace_fingerprint('1:aTOP:EXPOSED:N:W13:0,0;10,0;')
    need(validate_trace_roles(valid_width, {'N': 'HIGH_CURRENT'}, 12) == {'HIGH_CURRENT@13': 1},
         'positive role-width parser canary failed')
    checks += reject_canary(lambda: validate_trace_roles(valid_width, {'N': 'SIGNAL'}, 12),
                            'wrong literal role width')
    checks += reject_canary(lambda: validate_trace_roles(
        valid_width, {'N': 'HIGH_CURRENT'}, 12, {'HIGH_CURRENT': 1}),
        'obsolete naked-role width census')
    checks += reject_canary(lambda: retained_metric_status(
        101, 100, '==', 'baseline', 'saved baseline exact metric'),
        'baseline exact metric mutation')
    need(retained_metric_status(101, 100, '==', 'current', 'saved current exact metric') ==
         'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW',
         'current exact metric transition was not reported for review')
    need(retained_metric_status(100, 100, '==', 'current', 'saved current exact metric') ==
         'CURRENT_EXACT_METRIC_MATCH_NOT_ADOPTED',
         'matching current exact metric was incorrectly treated as adoption')
    checks += reject_canary(lambda: retained_metric_status(
        101, 100, '<=', 'current', 'saved current comparative metric'),
        'current comparative bound mutation')
    checks += reject_canary(lambda: retained_metric_status(
        101, 100, '<=', 'current', 'default current comparative metric'),
        'default current comparative bound remains fail-fast')
    checks += reject_canary(lambda: retained_metric_status(
        101, 100, '<=', 'baseline', 'saved baseline comparative metric', [],
        {'side': 'baseline', 'profile': 'SAVED_PROFILE_7', 'seed': '42'}),
        'baseline comparative bound remains fatal in collection mode')
    checks += reject_canary(lambda: validate_collection_scope(True, 'SAVED_7_8_REP'),
                            'bound-failure collection requires the complete cohort union')
    validate_collection_scope(True, None)
    synthetic_failures = []
    synthetic_context = {
        'failureKind': 'SAME_SIDE_SAVED11_TO_SAVED10_AREA',
        'cohortId': 'SAVED_9_10_REP', 'caseOrdinal': 1,
        'sourceId': 'PcbFunctionalGroupingContractTest',
        'metric': 'boardOutlineArea', 'literal': 'previousArea from same seed version10 result',
        'sourceCondition': 'synthetic version11 <= same-seed version10',
        'side': 'current', 'profile': 'SAVED_PROFILE_11',
        'placementInterpretationVersion': 11, 'placementDensityVersion': 11, 'seed': '42',
    }
    synthetic_status = retained_metric_status(101, 100, '<=', 'current',
        'synthetic current comparative metric', synthetic_failures, synthetic_context)
    need(synthetic_status == 'FAIL_RETAINED_BOUND' and len(synthetic_failures) == 1 and
         synthetic_failures[0].get('actual') == 101 and
         synthetic_failures[0].get('operator') == '<=' and
         synthetic_failures[0].get('expected') == 100 and
         synthetic_failures[0].get('sourceCondition') == synthetic_context['sourceCondition'] and
         synthetic_failures[0].get('side') == 'current' and
         synthetic_failures[0].get('profile') == 'SAVED_PROFILE_11' and
         synthetic_failures[0].get('seed') == '42',
         'current comparative failure was not accumulated with its bound evidence')
    need(comparison_outcome(None, synthetic_failures) == ('FAIL_RETAINED_BOUNDS', 2),
         'collected retained-bound failure did not produce report-fail/exit-2')
    synthetic_cohort = {'id': 'SAVED_9_10_REP', 'cohort': 'representative'}
    synthetic_rows = {'SAVED_9_10_REP': {'current': {'cases': [
        {'placementInterpretationVersion': 10, 'rootSeed': '42', 'boardArea': 100},
        {'placementInterpretationVersion': 11, 'rootSeed': '42', 'boardArea': 101,
         'cohortId': 'SAVED_9_10_REP', 'caseOrdinal': 1,
         'placementInterpretation': 'SAVED_PROFILE_11', 'placementDensityVersion': 11},
    ]}}}
    synthetic_catalog = {('SAVED_9_10_REP', 11, '42'): {'sourceExpectations': {
        'PcbFunctionalGroupingContractTest': {'areaComparisons': [{
            'literal': 'previousArea from same seed version10 result', 'value': 95,
            'operator': '<=', 'sourceCondition': 'synthetic version11 <= same-seed version10'}
        ]}}}}
    dynamic_failures = []
    dynamic_canary = compare_dynamic_saved10_areas(synthetic_rows, [synthetic_cohort],
        synthetic_catalog, 'current', dynamic_failures)
    need(len(dynamic_canary) == 1 and dynamic_canary[0].get('status') == 'FAIL_RETAINED_BOUND' and
         len(dynamic_failures) == 1 and dynamic_failures[0].get('actual') == 101 and
         dynamic_failures[0].get('expected') == 100,
         'same-side saved11-to-saved10 failure was not accumulated')
    mixed_roles = parse_trace_fingerprint(
        '1:aTOP:EXPOSED:N_H:W13:0,0;10,0;'
        '1:bTOP:EXPOSED:N_S:W11:0,20;10,20;'
        '1:cTOP:EXPOSED:N_C:0,40;10,40;')
    need(validate_trace_roles(mixed_roles, {
        'N_H': 'HIGH_CURRENT', 'N_S': 'SUPPLY', 'N_C': 'SIGNAL'}, 12) == {
            'HIGH_CURRENT@13': 1, 'SIGNAL@9': 1, 'SUPPLY@11': 1},
         'positive v12 mixed role@width census canary failed')
    width9_default = parse_trace_fingerprint('1:aTOP:EXPOSED:N:0,0;10,0;')
    need(validate_trace_roles(width9_default, {'N': 'SIGNAL'}, 11) == {'SIGNAL@9': 1},
         'positive implicit width9 parser canary failed')
    checks += reject_canary(lambda: parse_trace_fingerprint(
        '1:aTOP:EXPOSED:N:W9:0,0;10,0;'), 'noncanonical explicit width9 marker')
    clear_pair_ok = parse_trace_fingerprint(
        '1:aTOP:EXPOSED:A:W13:0,0;10,0;1:bTOP:EXPOSED:B:0,17;10,17;')
    clear_segments, _, _ = trace_segment_inventory(clear_pair_ok)
    need(independent_width_clearance(clear_pair_ok, clear_segments)[0] == 1,
         'positive mixed-width clearance boundary canary failed')
    clear_pair_bad = parse_trace_fingerprint(
        '1:aTOP:EXPOSED:A:W13:0,0;10,0;1:bTOP:EXPOSED:B:0,16;10,16;')
    bad_segments, _, _ = trace_segment_inventory(clear_pair_bad)
    checks += reject_canary(lambda: independent_width_clearance(clear_pair_bad, bad_segments),
                            'subthreshold role-width clearance')
    raster_traces = parse_trace_fingerprint(
        '1:hTOP:EXPOSED:A:0,0;2,0;1:vTOP:EXPOSED:A:1,-1;1,1;')
    raster_components = [{'terminals': [{'attachment': 'PLATED_THROUGH_HOLE', 'x': 0, 'y': 0}]}]
    raster_points, raster_steps = independent_layered_counts(raster_components, raster_traces, [])
    need(raster_points == 6 and raster_steps == 4,
         'positive independent raster intersection/pad-barrel canary failed')
    checks += reject_canary(lambda: independent_layered_counts(
        [{'terminals': [{'attachment': 'SURFACE_MOUNT', 'x': 0, 'y': 0}]}], raster_traces, []),
        'unobserved non-PTH raster attachment')
    first_case = copy.deepcopy(baseline_native['cases'][0])
    version, seed, ordinal = cohort_case_order(cohort)[0]
    def reject_case_mutation(field, value, label):
        mutated = copy.deepcopy(first_case)
        mutated[field] = value
        return reject_canary(lambda: validate_case(mutated, cohort, ordinal, version, seed,
            'baseline', catalog_rows[(cohort['id'], version, seed)], source_texts, arrays, 22), label)
    checks += reject_case_mutation('rootSeed', '999', 'case seed identity')
    checks += reject_case_mutation('placementInterpretationVersion', version + 1,
                                   'case saved-profile interpretation identity')
    checks += reject_case_mutation('traceWidthProfile', 'ROLE_TRACE@1',
                                   'case trace-width policy identity')
    checks += reject_case_mutation('expectedPads', first_case['expectedPads'] + 1,
                                   'declared plan pad census')
    current_first_case = copy.deepcopy(current_cohort['observation']['cases'][0])
    malformed_current_physical = copy.deepcopy(current_first_case)
    malformed_current_physical['boardArea'] = 'not-an-integer'
    checks += reject_canary(lambda: validate_case(
        malformed_current_physical, cohort, ordinal, version, seed, 'current',
        catalog_rows[(cohort['id'], version, seed)], source_texts, arrays, 24, []),
        'malformed current physical data remains fatal in collection mode')
    # Bind launcher-side input auditing to the complete sealed pin-file bytes.
    checks += reject_canary(lambda: verify_pin_file_binding(
        baseline_cohort['pinRaw'] + b'\n', baseline_cohort['result']['inputPinsSha256'], 1059),
        'source-pin byte mutation after native run')
    need(checks >= 12, 'saved-profile reader negative-canary set is unexpectedly small')
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--catalog-sha256', required=True)
    parser.add_argument('--runs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cohort', choices=sorted(COHORT_BY_ID), default=None)
    parser.add_argument('--collect-bound-failures', action='store_true',
                        help='finish the complete audit and report retained comparison failures with exit 2')
    args = parser.parse_args()
    validate_collection_scope(args.collect_bound_failures, args.cohort)
    need(not args.output.exists(), 'refusing to overwrite an existing reader output')
    repo_root = Path(__file__).resolve().parents[3]
    catalog_raw = read_bounded(args.catalog, MAX_CATALOG_BYTES, 'frozen baseline catalog')
    catalog = validate_catalog_bytes(catalog_raw, args.catalog_sha256)
    selected_id = args.cohort
    run_spec, run_paths, run_spec_sha = load_run_spec(args.runs, repo_root)
    selected_cohorts = [COHORT_BY_ID[selected_id]] if selected_id else COHORTS
    for cohort in selected_cohorts:
        for side in ['baseline', 'current']:
            for key, path in run_paths[cohort['id']][side].items():
                need(path.is_file(), 'selected run input is missing: ' + cohort['id'] + '/' + side + '/' + key)
                run_paths[cohort['id']][side][key] = path.resolve(strict=True)

    source_audits = {}
    baseline_pin_maps = {}
    baseline_source_roots = []
    for cohort in selected_cohorts:
        audit, baseline_pin_map, baseline_source_root = compare_source_pins(
            run_paths[cohort['id']]['baseline'], run_paths[cohort['id']]['current'])
        source_audits[cohort['id']] = audit
        baseline_pin_maps[cohort['id']] = baseline_pin_map
        baseline_source_roots.append(baseline_source_root)
    need(bool(baseline_source_roots) and
         all(root == baseline_source_roots[0] for root in baseline_source_roots),
         'selected baseline runs do not share one immutable source snapshot root')
    baseline_source_root = baseline_source_roots[0]
    baseline_pin_map = baseline_pin_maps[selected_cohorts[0]['id']]
    baseline_source_hashes = {
        path: baseline_pin_map.get(path) for path in [
            'tests/contracts/PcbFunctionalGroupingContractTest.java',
            'tests/contracts/PcbPocketPackingContractTest.java',
        ]
    }
    need(all(hashes.get(path) == baseline_source_hashes[path]
             for hashes in baseline_pin_maps.values() for path in baseline_source_hashes),
         'saved-profile baseline source-consumer pins differ across cohort snapshots')
    source_texts, arrays_by_source, source_catalog_bindings = load_source_catalog(
        baseline_source_root, catalog, baseline_pin_map)
    catalog_rows = validate_catalog_rows(catalog, source_texts, arrays_by_source)

    side_results = {}
    first_source_audit = source_audits[selected_cohorts[0]['id']]
    baseline_pin_sha = first_source_audit['baselinePinsSha256']
    current_pin_sha = first_source_audit['currentPinsSha256']
    need(all(audit['baselinePinsSha256'] == baseline_pin_sha and
             audit['currentPinsSha256'] == current_pin_sha
             for audit in source_audits.values()),
         'selected native cohorts did not consume one sealed baseline/current source snapshot pair')
    cohort_outputs = []
    retained_bound_failures = []
    negative_count = None
    for cohort_index, cohort in enumerate(selected_cohorts):
        pair = {}
        for side, layout_version in [('baseline', 22), ('current', 24)]:
            pair[side] = read_side(
                run_paths[cohort['id']][side], cohort, side, layout_version,
                catalog_rows, source_texts, arrays_by_source,
                retain_raw=(cohort_index == 0),
                bound_failures=retained_bound_failures
                    if args.collect_bound_failures else None)
        old = pair['baseline']
        new = pair['current']
        input_comparison = source_audits[cohort['id']]
        need(old['pinFileSha256'] == input_comparison['baselinePinsSha256'] and
             new['pinFileSha256'] == input_comparison['currentPinsSha256'],
             'launcher input pin file changed between case validation and comparison')
        need(old['consolePreambleSha256'] == new['consolePreambleSha256'] and
             old['consolePreambleBytes'] == new['consolePreambleBytes'],
             'baseline/current exact host/compiler console preambles differ')
        need(old['pinFileSha256'] == baseline_pin_sha and new['pinFileSha256'] == current_pin_sha,
             'selected native cohorts did not consume the same sealed baseline/current snapshots')
        pair_comparisons = compare_logical_cases(old['cases'], new['cases'], cohort)
        if cohort_index == 0:
            negative_count = negative_canaries(
                catalog_raw, args.catalog_sha256, source_texts, arrays_by_source,
                catalog_rows, cohort, old, new)
        cohort_outputs.append({
            'cohortId': cohort['id'],
            'baseline': {key: old[key] for key in ['summary', 'result', 'receiptSha256',
                'receiptBytes', 'consoleSha256', 'consoleBytes', 'consolePreambleSha256',
                'consolePreambleBytes', 'consolePreambleLineCount', 'pinFileSha256']},
            'current': {key: new[key] for key in ['summary', 'result', 'receiptSha256',
                'receiptBytes', 'consoleSha256', 'consoleBytes', 'consolePreambleSha256',
                'consolePreambleBytes', 'consolePreambleLineCount', 'pinFileSha256']},
            'inputComparison': input_comparison,
            'cases': pair_comparisons,
        })
        side_results[cohort['id']] = {
            'baseline': {'cases': old['cases']},
            'current': {'cases': new['cases']},
        }
    dynamic_checks = {
        side: compare_dynamic_saved10_areas(
            side_results, selected_cohorts, catalog_rows, side,
            retained_bound_failures if args.collect_bound_failures else None)
        for side in ['baseline', 'current']
    }
    dynamic_items = [item for group in dynamic_checks.values() for item in group]
    all_dynamic_pass = bool(dynamic_items) and all(
        item.get('status') == 'PASS_SAME_SIDE_SAVED10_OBSERVATION' for item in dynamic_items)
    all_dynamic_evaluated = bool(dynamic_items) and all(
        item.get('status') in {'PASS_SAME_SIDE_SAVED10_OBSERVATION', 'FAIL_RETAINED_BOUND'}
        for item in dynamic_items)
    if selected_id is None:
        need(len(dynamic_items) == 20 and all_dynamic_evaluated,
             'complete six-cohort reader requires all twenty saved11-to-saved10 source comparisons')
        if not args.collect_bound_failures:
            need(all_dynamic_pass,
                 'complete six-cohort reader requires all twenty saved11-to-saved10 source comparisons to pass')
    current_exact_metric_changes = []
    current_exact_geometry_changes = []
    for cohort_output in cohort_outputs:
        for case in cohort_output['cases']:
            case_identity = {
                'cohortId': cohort_output['cohortId'],
                'caseOrdinal': case['caseOrdinal'],
                'placementInterpretationVersion': case['placementInterpretationVersion'],
                'rootSeed': case['rootSeed'],
            }
            for bound in case['currentAreaSourceBounds']:
                if bound.get('status') == 'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW':
                    current_exact_metric_changes.append({
                        **case_identity, 'sourceId': bound['sourceId'],
                        'metric': bound['metric'], 'operator': bound['operator'],
                        'literal': bound['literal'], 'expected': bound['expected'],
                        'actual': bound['actual'], 'status': bound['status'],
                    })
            if case['currentOldSourceHashStatus'] == \
                    'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW':
                current_exact_geometry_changes.append({
                    **case_identity, 'sourceId': 'EXTERNAL_SAVED_PROFILE_CATALOG',
                    'metric': 'normalizedPhysicalSha256', 'operator': '==',
                    'literal': 'oldHash',
                    'expected': case['baselineNormalizedPhysicalSha256'],
                    'actual': case['currentNormalizedPhysicalSha256'],
                    'status': case['currentOldSourceHashStatus'],
                })
    selected_only = selected_id is not None
    output_status, output_exit_code = comparison_outcome(
        selected_id, retained_bound_failures if args.collect_bound_failures else [])
    physical_audit_scope = (
        'FINITE_SELECTED_COHORT_ONLY; NOT_A_COMPLETE_CROSS_COHORT_BOUND_AUDIT'
        if selected_only else
        'FULL_SIX_COHORTS_72_CASES_PER_SOURCE; INPUTS_FRAMING_GEOMETRY_ELECTRICAL_IDENTITY_AND_INDEPENDENT_PHYSICAL_ORACLES_COMPLETED; ' +
        ('RETAINED_BOUND_FAILURES_REPORTED' if retained_bound_failures else
         'ALL_RETAINED_BOUNDS_PASSED'))
    result = {
        'protocol': 'p07-saved-profile-epoch-reader-v2',
        'status': output_status, 'exitCode': output_exit_code,
        'scope': SCOPE, 'layoutQualification': False, 'adopted': False, 'adoption': False,
        'automaticGoldenSeal': False,
        'physicalAuditScope': physical_audit_scope,
        'retainedBoundFailureMode': 'COLLECT_AND_EXIT_2' if args.collect_bound_failures else 'FAIL_FAST',
        'retainedBoundFailuresCount': len(retained_bound_failures),
        'retainedBoundFailures': retained_bound_failures,
        'cohortSelection': selected_id or 'ALL_SIX_COHORTS',
        'cohortCount': len(selected_cohorts), 'caseCountPerSource': len(selected_cohorts) * 12,
        'baselineLayoutVersion': 22, 'baselineDependencyEpoch': 'tsj-generation-dependencies-v23',
        'currentLayoutVersion': 24, 'currentDependencyEpoch': 'tsj-generation-dependencies-v25',
        'baselineInputRevision': 2, 'currentInputRevision': 3,
        'catalogSha256': sha_bytes(catalog_raw), 'runSpecSha256': run_spec_sha,
        'sourceInputTransition': {'baselinePinsSha256': baseline_pin_sha,
            'currentPinsSha256': current_pin_sha, 'baselineCount': 1059, 'currentCount': 1059,
            'allowedCommonChanges': COMMON_CHANGED, 'allowedAddedCurrentTests': ADDED_TESTS,
            'allowedRemovedBaselineTests': REMOVED_TESTS},
        'baselineSourceCatalogBinding': {
            'sourceRootConsistentAcrossSelectedCohorts': True,
            'sourceExpectationsReadFrom': 'BASELINE_NORMALIZER_SNAPSHOT_ROOT',
            'sourceFiles': source_catalog_bindings,
        },
        'consolePreamble': {'exactRecognizedHostPrefix': True,
            'bytesIdenticalBaselineCurrent': True, 'lineCount': len(HOST_PREFIX_LINES)},
        'sameSideSaved10Comparisons': dynamic_checks,
        'sameSideSaved10ComparisonCount': len(dynamic_items),
        'sameSideSaved10ComparisonsComplete': all_dynamic_evaluated,
        'sameSideSaved10ComparisonsPassed': all_dynamic_pass,
        'currentExactMetricReview': {
            'status': 'CHANGED_CURRENT_EXACT_METRIC_REQUIRES_REVIEW'
                if current_exact_metric_changes else 'NO_CURRENT_EXACT_METRIC_CHANGES',
            'required': bool(current_exact_metric_changes),
            'changes': current_exact_metric_changes,
            'automaticGoldenSeal': False,
        },
        'currentExactGeometryReview': {
            'status': 'CHANGED_CURRENT_EXACT_GEOMETRY_REQUIRES_REVIEW'
                if current_exact_geometry_changes else 'NO_CURRENT_EXACT_GEOMETRY_CHANGES',
            'required': bool(current_exact_geometry_changes),
            'changes': current_exact_geometry_changes,
            'automaticGoldenSeal': False,
        },
        'negativeCanaries': negative_count,
        'normalizationBoundary': 'FULL_PINNED_SOURCE_HASH_AND_FOUR_EPOCH_NEUTRAL_LITERAL_SUBSTITUTIONS',
        'cohorts': cohort_outputs,
        'readerSha256': sha_bytes(Path(__file__).read_bytes()),
        'helperSha256': {
            'read_diagnostic.py': sha_bytes(Path(__file__).with_name('read_diagnostic.py').read_bytes()),
            'read_geometry_transition.py': sha_bytes(Path(__file__).with_name('read_geometry_transition.py').read_bytes()),
        },
    }
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2, ensure_ascii=True, allow_nan=False) + '\n')
    if output_exit_code:
        print('FAIL: retained saved-profile bounds; status=' + result['status'] +
              '; failures=' + str(result['retainedBoundFailuresCount']) +
              '; cases/source=' + str(result['caseCountPerSource']) +
              '; layout qualification=false; adoption=false; automatic golden seal=false; ' +
              'negative canaries=' + str(negative_count))
    else:
        print('PASS: saved-profile epoch inputs and observations; status=' + result['status'] +
              '; cases/source=' + str(result['caseCountPerSource']) +
              '; layout qualification=false; adoption=false; negative canaries=' + str(negative_count))
    if output_exit_code:
        raise SystemExit(output_exit_code)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
