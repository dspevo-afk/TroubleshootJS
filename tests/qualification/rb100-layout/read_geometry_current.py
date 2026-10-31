#!/usr/bin/env python3
"""Compare the frozen layout22 source with the qualified AN and AO layout25 sources.

The old transition reader remains immutable. This successor binds a versioned,
hash-pinned source context, reuses its pure geometry validators, and admits only
the named baseline22/dependency23, reference24/dependency25, and current25/
dependency26 contexts. It never updates or blesses a golden automatically.
"""
import argparse
import hashlib
import json
import os
import re
import stat
from pathlib import Path

from read_diagnostic import strict_json, need, integer
import read_geometry_transition as geometry

PROTOCOL = 'p07-final-source-six-board-context-v1'
CONTEXT_SCHEMA = 'troubleshootjs-q100-final-source-context-v1'
CORE_SHA256 = '3aa4fa311908c87ba9f68485048fee1d1a2680192e06679eb66c2b68c01779f5'
DIAGNOSTIC_SHA256 = '31f65be5e11472c7b09e8141292402c641df6b735787d32eb4c904ae098981e7'
BASELINE_PIN_SHA256 = '09ac49088fef03a2a0aee0929647f0e16b14bae15573faa89e71e19ad7d525da'
REFERENCE_PIN_SHA256 = '42e4739fb5baf1c124e38db2e9f4713c28c53e93cdbba92eba554d0a10a1cb69'
REFERENCE_GEOMETRY_RESULT_SHA256 = '4f4fe1330c703f9b700ba3ef31a5d048b5278b43af08117ef0cdc1d0984990ee'
REFERENCE_SAVED_PROFILE_RESULT_SHA256 = '505a7a0bf7d653fba1d316195e705c3e22e23dbb0676621fb8f67cdd8ae35c3e'
BASELINE_LAYOUT = 22
REFERENCE_LAYOUT = 24
CURRENT_LAYOUT = 25
BASELINE_CONTEXT = 'BASELINE_LAYOUT22_DEP23'
REFERENCE_CONTEXT = 'QUALIFIED_AN_LAYOUT24_DEP25'
CURRENT_CONTEXT = 'AO_FINAL_LAYOUT25_DEP26'
CURRENT_INPUT_COUNT = 1061
CURRENT_PIN_SHA256 = 'd4645b50fc04666572c971becebacc104c0ed25f8562477998340ee29bd92d81'
CURRENT_SOURCE_SNAPSHOT_REVISION = 5
PROCESS_MODULE_PATH = 'scripts/VerifierIsolation.psm1'
REFERENCE_PROCESS_MODULE_SHA256 = 'bbca483ef2dee5dd29eef3857126591e3c0556cf3ba76f6f6d62124e910ae7e5'
CURRENT_PROCESS_MODULE_SHA256 = '8a7eff1639e973e6e4ade9a72716274fcd95caece0074f19ede72817d01f2e77'
PREVIOUS_CURRENT_PIN_SHA256 = '6d94be7160b49bff6291b2544bf439b4374fda0eb77f13e84f7bb9c7725b028f'
PREVIOUS_CURRENT_SOURCE_SNAPSHOT_REVISION = 4
PREVIOUS_CURRENT_DIAGNOSTIC_SHA256 = '6a760356410ccd3de76384dbbbae27f9dbc86ac96653d7fbd9c0db1e995c9036'
CURRENT_DIAGNOSTIC_SHA256 = '0621772088d4a42ce40ad35f45e0933fe54cdf0380bd410adf7def9667227b85'
DIAGNOSTIC_HELPER_PATH = 'tests/contracts/Rb100PhysicalDiagnostics.java'
PREVIOUS_CURRENT_PIN_BASENAME = 'ao-final-native-v4-sealed-pins.json'
PREVIOUS_CURRENT_INPUTS_BASENAME = 'ao-final-native-inputs-4'
CURRENT_INPUTS_BASENAME = 'ao-final-native-inputs-5'
Q3_GEOMETRY_READER_SHA256 = 'ab04a671ee052e197a921d86bc335953946e1b509bc627b5aa99081a9f2b6a2d'
Q3_GEOMETRY_COMPARISON_SHA256 = '2d0507114df5849a6805381fd438f5c9acb49a505b3a51fe30b59772643dabd9'
Q3_GEOMETRY_PIN_SHA256 = '5e79aa4cd2496c6f64f4a8bb63fc0e3c3624cc5669ab31d7798588c92d72502d'
NATIVE_CHILD_LIMIT_MS = 180000
OUTER_WATCHDOG_MS = 600000
MAX_CONTEXT_BYTES = 1024 * 1024
MAX_PIN_BYTES = 8 * 1024 * 1024
MAX_NATIVE_BYTES = 8 * 1024 * 1024
MAX_RESULT_BYTES = 1024 * 1024
PASS_LINE = 'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP_LINE = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
TRANSCRIPT_PREFIX = 'Transcript stopped, output file is '
TRANSCRIPT_STARS = '**********************'
PS_TRANSCRIPT_END = 'Windows PowerShell transcript end'

Q_COMPACT2_EXPECTED = {
    '-1017': '02a69383d57ee6951e5c50f5dffe20dd5b2555ae7154b128f42d27f2b0dcd072',
    '-1004': 'b76292d8d3b597ff80b95f736bd23cddde49c4c48cb1c62af43d694be97ed35d',
    '-1022': 'a713acba0dac5dbafd6835830fb8a3685561a591fdf35686495b81d263c0d502',
}
LAYOUT23_COMPACT2_REFERENCE = [
    '7b9578e0e69baf894457283f76148968700ed0a4d53101dc2e2a59d317dbb5d9',
    '1d8cbdfe7d82502f8a94be04abdff08db48aaeb7929f04a8a8234fd9b98a2201',
    'ecfdf23e36593b585f71b02dc56d37d2920a80e6d1192dca44223ec377be8087',
]
LAYOUT23_LEGACY1_REFERENCE = [
    '8e55d14562bac9f7b78bd4e0d0c4d386766c905944223f85aa693bbbadcf2873',
    '291d1d12ab5ed4e5379bc7a359a5ddc6977bf6cdcbb970196beba9eee00b8028',
    '08e13b77a5639a33a81783c6a083dea3d266446d047f9194225c0a24c78f24b7',
]
Q3_LAYOUT25_LEGACY1_EXPECTED = {
    '-1017': '81e93a9f0a80a711d9eb0c527b7dbdfcfa455b11920977ea46622c1aedefee41',
    '-1004': 'fbf29057aed8d0bb4b0dca7e4dbf68a323dd05e5108dddd0a265c27dcb03ecbf',
    '-1022': 'ade3126987e5d4ef4529d0d171f68f8977ac8b0174efa47a43b63f196c0fe3a9',
}
ALLOWED_REFERENCE_TO_CURRENT_CHANGED = sorted([
    'scripts/VerifierIsolation.psm1',
    'src/com/lushprojects/circuitjs1/client/GenerationDependencyContext.java',
    'src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java',
    'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
    'scripts/verify-current-contracts.ps1',
    'tests/contracts/D01DiagnosticContractTest.java',
    'tests/contracts/P07BranchDiagnosticsContractTest.java',
    'tests/contracts/P07GeometryEpochDiagnosticContractTest.java',
    'tests/contracts/PcbCompactnessContractTest.java',
    'tests/contracts/PcbNormalPackingIdentityContractTest.java',
    'tests/contracts/Rb100PhysicalDiagnostics.java',
    'tests/contracts/Rb56ModelIdentityContractTest.java',
    'tests/contracts/RelBSessionIdentityContractTest.java',
])
ALLOWED_REFERENCE_TO_CURRENT_ADDED = sorted([
    'tests/contracts/P07OrderingCandidatesContractTest.java',
    'tests/contracts/P07RelaxedDistanceBoundContractTest.java',
])
ALLOWED_REFERENCE_TO_CURRENT_REMOVED = []
ALLOWED_BASELINE_TO_REFERENCE_CHANGED = sorted(geometry.COMMON_CHANGED + [
    'src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java',
    'tests/contracts/P07BranchDiagnosticsContractTest.java',
])
ALLOWED_BASELINE_TO_REFERENCE_ADDED = sorted(geometry.ADDED_TESTS)
ALLOWED_BASELINE_TO_REFERENCE_REMOVED = sorted(geometry.REMOVED_TESTS)
CURRENT_LAYOUT_PROJECTION_METHOD = '''
    /** Historical full-layout comparison projection; only the current layout prefix is changed. */
    static String layout24PrefixReferenceSha256(PcbBoardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("Missing PCB layout");
        if (SeededPcbLayoutGenerator.CURRENT_VERSION != 25)
            throw new AssertionError("Historical geometry projection requires current layout version 25");
        String before = layout.geometryFingerprint();
        String currentPrefix = "layout=25|";
        if (before == null || !before.startsWith(currentPrefix))
            throw new AssertionError("Expected actual current layout=25 geometry fingerprint");
        String projected = "layout=24|" + before.substring(currentPrefix.length());
        if (!before.equals(layout.geometryFingerprint()))
            throw new AssertionError("Historical geometry projection changed bytes beyond the layout prefix");
        return PlayerSessionFingerprint.of(projected);
    }
'''
CURRENT_ALLOWED_PATH_SHA256 = {
    'scripts/VerifierIsolation.psm1': CURRENT_PROCESS_MODULE_SHA256,
    'scripts/verify-current-contracts.ps1': '39fdce2d616db7bfe9801d8a8d15c2dffb748e3ba0597e6c792271015b5a0dec',
    'src/com/lushprojects/circuitjs1/client/GenerationDependencyContext.java': 'ba62d50ae3d91f81aafdd0429f8274e6e2fbf2ce38959cb84c83f57dce7223c9',
    'src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java': 'de9a9a70f05b4998607f196a90ac0d5696879c8da0c615369a80f3b3733d9657',
    'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java': 'b9fa34db5b55b6937865ef70249476ad71458048982979cc0713a95fe63e634e',
    'tests/contracts/D01DiagnosticContractTest.java': '68d3825b2f033e1f94f39e4c3e79085317e733c7efa2c8e66c15217aa38dd4c1',
    'tests/contracts/P07BranchDiagnosticsContractTest.java': '0c97dd4f0e1c3f4c18c34f80ca9c827f00639d17fa0599048ba9cf0c319f8867',
    'tests/contracts/P07GeometryEpochDiagnosticContractTest.java': 'ced65dad6431c1f10f3e17748d436ad3e4206fb8abac917d3350b74f5a6560aa',
    'tests/contracts/P07OrderingCandidatesContractTest.java': 'cc40b161f6706eceebf9785b6aeb3654f6bd560b581400519fe1410d80050837',
    'tests/contracts/P07RelaxedDistanceBoundContractTest.java': '90608e438b7f45dc54a0691f0a86c1f6f2d11a556cc56915a7e7829be2ca2764',
    'tests/contracts/PcbCompactnessContractTest.java': 'e39ef8bd2a53e035141f03ec8705a26bbae109cd2b47b7bb578af57d3e12162a',
    'tests/contracts/PcbNormalPackingIdentityContractTest.java': '9cc4e21d92505af13066074af1454ce16a5d00898f80dfea0245ccac4611a3ed',
    'tests/contracts/Rb100PhysicalDiagnostics.java': '0621772088d4a42ce40ad35f45e0933fe54cdf0380bd410adf7def9667227b85',
    'tests/contracts/Rb56ModelIdentityContractTest.java': '4e54cce9818151aae648aebcc8188c029deba67c05cff6332c81c39087c9cef5',
    'tests/contracts/RelBSessionIdentityContractTest.java': '228a800cf45f588ae887a083367b88669495f571dd64f3f0be5738c4e784c73e',
}


def sha_bytes(raw):
    return hashlib.sha256(raw).hexdigest()


def read_bounded(path, limit, label):
    path = Path(path)
    need(path.is_file(), label + ' is not a regular file')
    size = path.stat().st_size
    need(0 < size <= limit, label + ' exceeds its transport bound')
    return path.read_bytes()


def load_pinned_helpers():
    here = Path(__file__).resolve(strict=True).parent
    core_path = here / 'read_geometry_transition.py'
    diagnostic_path = here / 'read_diagnostic.py'
    need(sha_bytes(read_bounded(core_path, 2 * 1024 * 1024, 'historical geometry reader')) == CORE_SHA256,
         'historical geometry reader bytes differ from the frozen pure-validator implementation')
    need(sha_bytes(read_bounded(diagnostic_path, 1024 * 1024, 'diagnostic JSON helper')) == DIAGNOSTIC_SHA256,
         'diagnostic helper bytes differ from the frozen implementation')
    need(Path(geometry.__file__).resolve(strict=True) == core_path,
         'imported historical geometry reader resolved outside this source directory')
    return here


def context_fields(context, label):
    fields = {'contextId', 'layoutVersion', 'dependencyEpoch', 'inputRevision',
              'sourceSnapshotRevision', 'inputCount', 'pinsSha256'}
    need(type(context) is dict and set(context) == fields,
         label + ' source-context field set differs')
    need(type(context.get('contextId')) is str and context['contextId'] and
         re.fullmatch(r'[0-9a-f]{64}', context.get('pinsSha256', '')) is not None and
         all(type(context.get(key)) is int for key in
             ['layoutVersion', 'inputRevision', 'sourceSnapshotRevision', 'inputCount']),
         label + ' source-context identity or integer fields are malformed')
    need(context['inputCount'] > 0 and context['inputRevision'] > 0 and
         context['sourceSnapshotRevision'] > 0,
         label + ' source-context revisions/count must be positive')
    need(context['dependencyEpoch'] == 'tsj-generation-dependencies-v' +
         str(context['layoutVersion'] + 1),
         label + ' layout/dependency epoch pair differs')
    return context


def load_source_context(path, expected_sha256):
    raw = read_bounded(path, MAX_CONTEXT_BYTES, 'versioned final-source context')
    need(type(expected_sha256) is str and re.fullmatch(r'[0-9a-f]{64}', expected_sha256) is not None and
         sha_bytes(raw) == expected_sha256,
         'versioned final-source context digest differs from the supplied frozen digest')
    context = strict_json(raw.decode('utf-8-sig'))
    validate_source_context_object(context)
    return context, raw, sha_bytes(raw)


def validate_source_context_object(context):
    need(type(context) is dict and set(context) == {
        'schema', 'candidateId', 'contexts', 'referenceQualification', 'referenceToCurrent'},
        'versioned final-source context has unknown/missing fields')
    need(context.get('schema') == CONTEXT_SCHEMA and
         context.get('candidateId') == 'AO_FINAL_LAYOUT25_DEP26',
         'final-source context schema/candidate differs')
    contexts = context.get('contexts')
    need(type(contexts) is dict and set(contexts) == {'baseline', 'reference', 'current'},
         'final-source context must bind baseline, qualified reference, and current')
    baseline = context_fields(contexts['baseline'], 'baseline')
    reference = context_fields(contexts['reference'], 'reference')
    current = context_fields(contexts['current'], 'current')
    need(baseline == {
        'contextId': BASELINE_CONTEXT, 'layoutVersion': 22,
        'dependencyEpoch': 'tsj-generation-dependencies-v23', 'inputRevision': 2,
        'sourceSnapshotRevision': 1, 'inputCount': 1059, 'pinsSha256': BASELINE_PIN_SHA256,
    }, 'baseline source context is not the frozen22/23 input')
    need(reference == {
        'contextId': REFERENCE_CONTEXT, 'layoutVersion': 24,
        'dependencyEpoch': 'tsj-generation-dependencies-v25', 'inputRevision': 3,
        'sourceSnapshotRevision': 2, 'inputCount': 1059, 'pinsSha256': REFERENCE_PIN_SHA256,
    }, 'qualified AN source context is not the frozen24/25 input')
    need(current['contextId'] == CURRENT_CONTEXT and current['layoutVersion'] == CURRENT_LAYOUT and
         current['dependencyEpoch'] == 'tsj-generation-dependencies-v26' and
         current['inputRevision'] == 3 and current['inputCount'] == CURRENT_INPUT_COUNT and
         current['sourceSnapshotRevision'] == CURRENT_SOURCE_SNAPSHOT_REVISION and
         current['pinsSha256'] == CURRENT_PIN_SHA256,
         'AO current source context is not the explicit25/26 successor input')
    qualification = context.get('referenceQualification')
    need(type(qualification) is dict and qualification == {
        'geometryComparisonSha256': REFERENCE_GEOMETRY_RESULT_SHA256,
        'savedProfileComparisonSha256': REFERENCE_SAVED_PROFILE_RESULT_SHA256,
    }, 'qualified AN reference evidence digests differ')
    transition = context.get('referenceToCurrent')
    need(type(transition) is dict and set(transition) == {
        'commonChangedPaths', 'addedPaths', 'removedPaths'},
        'reference-to-current source transition fields differ')
    for key in ['commonChangedPaths', 'addedPaths', 'removedPaths']:
        values = transition[key]
        need(type(values) is list and values == sorted(set(values)) and
             all(valid_relative_source_path(item) for item in values),
             'reference-to-current path list is malformed: ' + key)
    need(transition['commonChangedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_CHANGED and
         transition['addedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_ADDED and
         transition['removedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_REMOVED,
         'reference-to-current path delta expands beyond the closed AO source allowlist')
    need('tests/contracts/P07GeometryEpochDiagnosticContractTest.java' in
         transition['commonChangedPaths'] and
         'tests/contracts/PcbCompactnessContractTest.java' in transition['commonChangedPaths'],
         'final-source context omits the diagnostic/normalizer source transition')
    return True


def valid_relative_source_path(value):
    return (type(value) is str and value and value == value.replace('\\', '/') and
            not value.startswith('/') and re.match(r'^[A-Za-z]:', value) is None and
            '..' not in value.split('/') and '\x00' not in value)


def load_pin_manifest(path, expected_context, label):
    raw = read_bounded(path, MAX_PIN_BYTES, label + ' source-pin manifest')
    need(sha_bytes(raw) == expected_context['pinsSha256'],
         label + ' source-pin manifest digest differs from the explicit context')
    rows = strict_json(raw.decode('utf-8-sig'))
    need(type(rows) is list and len(rows) == expected_context['inputCount'],
         label + ' source-pin count differs from its explicit context')
    result = {}
    for row in rows:
        need(type(row) is dict and set(row) == {'path', 'bytes', 'sha256'} and
             valid_relative_source_path(row.get('path')) and
             type(row.get('bytes')) is int and row['bytes'] > 0 and
             type(row.get('sha256')) is str and re.fullmatch(r'[0-9a-f]{64}', row['sha256']) is not None and
             row['path'] not in result,
             label + ' source-pin row is malformed or duplicated')
        result[row['path']] = {'bytes': row['bytes'], 'sha256': row['sha256']}
    need(len(result) == expected_context['inputCount'], label + ' source-pin census is incomplete')
    return result, raw


def is_reparse(path):
    info = path.lstat()
    reparse = getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 0x400)
    return path.is_symlink() or bool(getattr(info, 'st_file_attributes', 0) & reparse)


def audit_source_tree(root, expected, label):
    root = Path(root)
    need(root.exists() and not is_reparse(root), label + ' source root is absent or a reparse point')
    root = root.resolve(strict=True)
    need(root.is_dir(), label + ' source root is not a directory')
    actual = {}
    for directory, dirs, files in os.walk(root, topdown=True, followlinks=False):
        directory_path = Path(directory)
        for name in list(dirs):
            child = directory_path / name
            need(not is_reparse(child), label + ' source tree contains a reparse directory')
        for name in files:
            path = directory_path / name
            need(not is_reparse(path) and path.is_file(), label + ' source tree contains a reparse/non-file entry')
            key = path.relative_to(root).as_posix()
            raw = path.read_bytes()
            need(key not in actual, label + ' source tree contains a duplicate path')
            actual[key] = {'bytes': len(raw), 'sha256': sha_bytes(raw)}
    need(actual == expected,
         label + ' source tree bytes/path census differ from the sealed native manifest')
    return {'fileCount': len(actual), 'totalBytes': sum(row['bytes'] for row in actual.values()),
            'allBytesMatchSealedPins': True}


def source_map_transition(before, after):
    common = set(before) & set(after)
    changed = sorted(path for path in common if before[path]['sha256'] != after[path]['sha256'])
    return changed, sorted(set(before) - set(after)), sorted(set(after) - set(before))


def validate_transition_spec_against_maps(transition, reference, current):
    need(type(transition) is dict and set(transition) == {
        'commonChangedPaths', 'addedPaths', 'removedPaths'},
        'reference-to-current source transition specification is malformed')
    for key in ['commonChangedPaths', 'addedPaths', 'removedPaths']:
        values = transition[key]
        need(type(values) is list and values == sorted(set(values)) and
             all(valid_relative_source_path(item) for item in values),
             'reference-to-current source transition list is malformed: ' + key)
    changed, removed, added = source_map_transition(reference, current)
    need(changed == transition['commonChangedPaths'] and
         removed == transition['removedPaths'] and added == transition['addedPaths'],
         'reference-to-current source transition differs from the actual sealed maps')
    return {'commonChangedPaths': changed, 'removedPaths': removed, 'addedPaths': added}


def jar_map(source_map):
    return {path: row['sha256'] for path, row in source_map.items() if path.endswith('.jar')}


def validate_source_transitions(context, maps):
    baseline, reference, current = maps['baseline'], maps['reference'], maps['current']
    changed, removed, added = source_map_transition(baseline, reference)
    need(changed == ALLOWED_BASELINE_TO_REFERENCE_CHANGED and
         removed == ALLOWED_BASELINE_TO_REFERENCE_REMOVED and
         added == ALLOWED_BASELINE_TO_REFERENCE_ADDED,
         'baseline-to-qualified-reference transition differs from its closed09ac-to-42e allowlist')
    actual_reference_to_current = validate_transition_spec_against_maps(
        context['referenceToCurrent'], reference, current)
    need(actual_reference_to_current['commonChangedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_CHANGED and
         actual_reference_to_current['addedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_ADDED and
         actual_reference_to_current['removedPaths'] == ALLOWED_REFERENCE_TO_CURRENT_REMOVED,
         'actual reference-to-current source delta expands beyond the closed AO source allowlist')
    need(set(CURRENT_ALLOWED_PATH_SHA256) ==
         set(ALLOWED_REFERENCE_TO_CURRENT_CHANGED + ALLOWED_REFERENCE_TO_CURRENT_ADDED),
         'closed AO path/hash allowlist is internally inconsistent')
    for path, expected_hash in CURRENT_ALLOWED_PATH_SHA256.items():
        need(path in current and current[path]['sha256'] == expected_hash,
             'final source path/hash differs from its closed AO expectation: ' + path)
    need(PROCESS_MODULE_PATH in baseline and PROCESS_MODULE_PATH in reference and
         PROCESS_MODULE_PATH in current and
         baseline[PROCESS_MODULE_PATH]['sha256'] ==
         reference[PROCESS_MODULE_PATH]['sha256'] == REFERENCE_PROCESS_MODULE_SHA256 and
         current[PROCESS_MODULE_PATH]['sha256'] == CURRENT_PROCESS_MODULE_SHA256,
         'bounded-process module changed outside the exact reviewed source transition')
    for path in geometry.UNCHANGED_ORACLE_INPUTS:
        if path == PROCESS_MODULE_PATH:
            continue
        need(path in baseline and path in reference and path in current and
             baseline[path]['sha256'] == reference[path]['sha256'] == current[path]['sha256'],
             'geometry/reference oracle input changed across source contexts: ' + path)
    need(jar_map(baseline) == jar_map(reference) == jar_map(current),
         'JDK/GWT/compiler jars differ across source contexts')
    return {
        'baselineToQualifiedReference': {
            'commonChangedPaths': ALLOWED_BASELINE_TO_REFERENCE_CHANGED,
            'retiredPaths': ALLOWED_BASELINE_TO_REFERENCE_REMOVED,
            'addedPaths': ALLOWED_BASELINE_TO_REFERENCE_ADDED,
            'closed09acTo42eAllowlistMatched': True,
        },
        'qualifiedReferenceToCurrent': {
            'commonChangedPaths': actual_reference_to_current['commonChangedPaths'],
            'retiredPaths': actual_reference_to_current['removedPaths'],
            'addedPaths': actual_reference_to_current['addedPaths'],
            'actualTransitionMatchesContext': True,
            'closedAllowlistMatched': True,
            'expectedChangedPaths': ALLOWED_REFERENCE_TO_CURRENT_CHANGED,
            'expectedAddedPaths': ALLOWED_REFERENCE_TO_CURRENT_ADDED,
            'expectedRemovedPaths': ALLOWED_REFERENCE_TO_CURRENT_REMOVED,
            'boundedProcessModule': {
                'path': PROCESS_MODULE_PATH,
                'baselineAndReferenceSha256': REFERENCE_PROCESS_MODULE_SHA256,
                'currentSha256': CURRENT_PROCESS_MODULE_SHA256,
                'onlyChangedAfterQualifiedReference': True,
            },
        },
        'unchangedOraclePaths': sorted(path for path in geometry.UNCHANGED_ORACLE_INPUTS
                                       if path != PROCESS_MODULE_PATH),
        'unchangedJarCount': len(jar_map(baseline)),
    }


def source_file(root, source_map, relative, label):
    need(valid_relative_source_path(relative) and relative in source_map,
         label + ' is absent from its sealed source map')
    root = Path(root).resolve(strict=True)
    path = root / Path(*relative.split('/'))
    need(path.exists() and not is_reparse(path), label + ' path is absent or a reparse point')
    resolved = path.resolve(strict=True)
    try:
        resolved.relative_to(root)
    except ValueError:
        raise ValueError(label + ' escapes its source snapshot root')
    raw = read_bounded(resolved, 2 * 1024 * 1024, label)
    need(sha_bytes(raw) == source_map[relative]['sha256'] and
         len(raw) == source_map[relative]['bytes'], label + ' bytes differ from sealed source pins')
    return resolved, raw


def read_context_normalizer(root, source_map, layout, label):
    key = 'tests/contracts/PcbCompactnessContractTest.java'
    path, raw = source_file(root, source_map, key, label + ' compactness source')
    source = raw.decode('utf-8-sig')
    if label != 'current':
        boundary = geometry.normalizer_boundary(path, source_map[key]['sha256'], layout)
        return boundary, source
    block, projection_sha = current_layout25_epoch_neutral_normalizer(source)
    boundary = {
        'fullFileSha256': source_map[key]['sha256'],
        'epochNeutralCalledBlockSha256': sha_bytes(block.encode('utf-8')),
        'layoutVersion': layout,
        'allowedEpochLiteralDifferences': 4,
        'approvedCurrentPrefixProjectionRemoved': True,
        'approvedCurrentPrefixProjectionSha256': projection_sha,
    }
    return boundary, source


def current_layout25_epoch_neutral_normalizer(source):
    source = source.replace('\r\n', '\n')
    need('\r' not in source, 'current compactness source has bare-CR line endings')
    need(source.count(CURRENT_LAYOUT_PROJECTION_METHOD) == 1,
         'current compactness source does not contain exactly the approved layout24 prefix projection')
    adapted = source.replace(CURRENT_LAYOUT_PROJECTION_METHOD, '', 1)
    block = geometry.epoch_neutral_normalizer(adapted, CURRENT_LAYOUT)
    return block, sha_bytes(CURRENT_LAYOUT_PROJECTION_METHOD.encode('utf-8'))


def java_string_array(source, name):
    pattern = r'\b' + re.escape(name) + r'\s*=\s*new\s+String\[\]\s*\{([^{}]*)\}\s*;'
    matches = re.findall(pattern, source, re.S)
    need(len(matches) == 1, 'expected one final compactness hash array: ' + name)
    values = []
    for token in matches[0].split(','):
        token = token.strip()
        if not token:
            continue
        match = re.fullmatch(r'"([0-9a-f]{64})"', token)
        need(match is not None, 'malformed compactness hash literal in ' + name)
        values.append(match.group(1))
    need(len(values) == 3, 'compactness hash array must contain exactly three fixed seeds: ' + name)
    return values


def validate_compactness_hashes(source_map, roots):
    key = 'tests/contracts/PcbCompactnessContractTest.java'
    _, baseline_raw = source_file(roots['baseline'], source_map['baseline'], key,
                                  'baseline compactness source')
    _, reference_raw = source_file(roots['reference'], source_map['reference'], key,
                                   'reference compactness source')
    _, current_raw = source_file(roots['current'], source_map['current'], key,
                                 'current compactness source')
    reference_text = reference_raw.decode('utf-8-sig')
    current_text = current_raw.decode('utf-8-sig')
    current_hashes = java_string_array(current_text, 'CURRENT_LAYOUT25_COMPACT2_EXPECTED_SHA256')
    need(current_hashes == [Q_COMPACT2_EXPECTED[seed] for seed in ['-1017', '-1004', '-1022']],
         'final layout25 compact2 source hashes differ from the audited Q physical results')
    current_layout23 = java_string_array(current_text, 'LAYOUT23_COMPACT2_REFERENCE_SHA256')
    need(current_layout23 == LAYOUT23_COMPACT2_REFERENCE,
         'final source changed the independently frozen layout23 compact reference hashes')
    current_legacy1_layout23 = java_string_array(
        current_text, 'LAYOUT23_LEGACY1_REFERENCE_SHA256')
    need(current_legacy1_layout23 == LAYOUT23_LEGACY1_REFERENCE,
         'final source changed the independently frozen layout23 legacy1 reference hashes')
    current_legacy1 = java_string_array(
        current_text, 'CURRENT_LAYOUT25_LEGACY1_EXPECTED_SHA256')
    need(current_legacy1 == [Q3_LAYOUT25_LEGACY1_EXPECTED[seed]
                             for seed in ['-1017', '-1004', '-1022']],
         'final layout25 legacy1 hashes differ from the source-bound Q3 observation')
    # The independent source-chain rule below checks the helper body after only
    # four epoch literals are neutralized; unrelated helper changes stay visible.
    return {
        'baselineSourceSha256': sha_bytes(baseline_raw),
        'qualifiedReferenceSourceSha256': sha_bytes(reference_raw),
        'currentSourceSha256': sha_bytes(current_raw),
        'layout23Compact2ReferencesMatchFrozenReaderValues': current_layout23,
        'layout23Legacy1ReferencesMatchFrozenReaderValues': current_legacy1_layout23,
        'currentLayout25Compact2Expected': current_hashes,
        'currentExpectedHashesMatchAuditedQ': True,
        'currentLayout25Legacy1Expected': current_legacy1,
        'currentLegacy1ExpectedMatchesQ3Observation': True,
        'currentLegacy1Q3Evidence': {
            'geometryComparisonSha256': Q3_GEOMETRY_COMPARISON_SHA256,
            'geometryReaderSha256': Q3_GEOMETRY_READER_SHA256,
            'sourcePinManifestSha256': Q3_GEOMETRY_PIN_SHA256,
        },
    }


def diagnostic_text(source_map, root, label):
    key = geometry.DIAGNOSTIC_SOURCE_PATH
    resolved, raw = source_file(root, source_map, key, label + ' P07 diagnostic source')
    return resolved, raw.decode('utf-8')


REFERENCE_FINAL_DIAGNOSTIC_ADAPTERS = [
    ('current-layout-guard', 'layoutVersion == 24', 'layoutVersion == 25', 2),
    ('current-dependency-epoch', 'tsj-generation-dependencies-v25', 'tsj-generation-dependencies-v26', 2),
    ('current-context-label', 'layout24/dependency25', 'layout25/dependency26', 2),
]
def normalize_final_diagnostic_source(text, side):
    text = text.replace('\r\n', '\n')
    need('\r' not in text, side + ' P07 diagnostic source has bare-CR line endings')
    for name, reference_literal, current_literal, occurrences in REFERENCE_FINAL_DIAGNOSTIC_ADAPTERS:
        expected = reference_literal if side == 'reference' else current_literal
        forbidden = current_literal if side == 'reference' else reference_literal
        need(text.count(expected) == occurrences and text.count(forbidden) == 0,
             side + ' P07 diagnostic metadata literal count differs: ' + name)
        text = text.replace(expected, '<' + name.upper().replace('-', '_') + '>')
    return text


def validate_diagnostic_source_chain(roots, maps):
    _, baseline_text = diagnostic_text(maps['baseline'], roots['baseline'], 'baseline')
    _, reference_text = diagnostic_text(maps['reference'], roots['reference'], 'reference')
    _, current_text = diagnostic_text(maps['current'], roots['current'], 'current')
    old_boundary = geometry.diagnostic_source_boundary(
        roots['baseline'], roots['reference'],
        {path: row['sha256'] for path, row in maps['baseline'].items()},
        {path: row['sha256'] for path, row in maps['reference'].items()})
    reference_normalized = normalize_final_diagnostic_source(reference_text, 'reference')
    current_normalized = normalize_final_diagnostic_source(current_text, 'current')
    need(reference_normalized == current_normalized,
         'final P07 diagnostic changed beyond the explicit layout/dependency metadata adapters')
    mutation = current_text.replace('layoutVersion == 25', 'layoutVersion == 26', 1)
    try:
        normalize_final_diagnostic_source(mutation, 'current')
    except ValueError:
        mutation_rejected = True
    else:
        raise ValueError('diagnostic metadata mutation canary was accepted')
    return {
        'baselineToReference': old_boundary,
        'referenceRawSha256': sha_bytes(reference_text.encode('utf-8')),
        'currentRawSha256': sha_bytes(current_text.encode('utf-8')),
        'referenceToCurrentEpochNeutralSha256': sha_bytes(reference_normalized.encode('utf-8')),
        'sameNonmetadataDiagnosticBody': True,
        'allowedMetadataAdapters': [
            {'name': name, 'referenceLiteral': old, 'currentLiteral': new,
             'occurrences': count}
            for name, old, new, count in REFERENCE_FINAL_DIAGNOSTIC_ADAPTERS],
        'metadataMutationCanaryRejected': mutation_rejected,
    }


PREVIOUS_P07_BRANCH_ADVANCE_BLOCK = '''                terminal = valueUnit(budget, "p07-branch-session-advance-" + call,
                    new UnitValue<Boolean>() {
                        public Boolean run() {
                            return Boolean.valueOf(session[0].advanceSlice(2048));
                        }
                    }).booleanValue();'''
CURRENT_P07_BRANCH_ADVANCE_BLOCK = '''                terminal = valueUnit(budget, "p07-branch-session-advance-" + call,
                    new UnitValue<Boolean>() {
                        public Boolean run() {
                            // Keep one counted diagnostic unit on the production router quantum.
                            return Boolean.valueOf(session[0].advanceSlice());
                        }
                    }).booleanValue();'''


def previous_current_source_context():
    return {
        'contextId': CURRENT_CONTEXT,
        'layoutVersion': CURRENT_LAYOUT,
        'dependencyEpoch': 'tsj-generation-dependencies-v26',
        'inputRevision': 3,
        'sourceSnapshotRevision': PREVIOUS_CURRENT_SOURCE_SNAPSHOT_REVISION,
        'inputCount': CURRENT_INPUT_COUNT,
        'pinsSha256': PREVIOUS_CURRENT_PIN_SHA256,
    }


def load_previous_current_source_inputs(pins_path, inputs_root):
    pins_path = Path(pins_path)
    inputs_root = Path(inputs_root)
    need(pins_path.name == PREVIOUS_CURRENT_PIN_BASENAME and
         inputs_root.name == PREVIOUS_CURRENT_INPUTS_BASENAME and
         pins_path.parent.resolve(strict=True) == inputs_root.parent.resolve(strict=True),
         'previous-current source arguments are outside the fixed snapshot4 paths')
    need(pins_path.is_file() and not is_reparse(pins_path) and
         inputs_root.is_dir() and not is_reparse(inputs_root),
         'previous-current snapshot4 pin/root path is absent or a reparse point')
    source_map, pin_raw = load_pin_manifest(
        pins_path, previous_current_source_context(), 'previous-current')
    need(sha_bytes(pin_raw) == PREVIOUS_CURRENT_PIN_SHA256,
         'previous-current source snapshot is not the fixed snapshot4 manifest')
    return source_map, inputs_root.resolve(strict=True)


def normalize_branch_diagnostic_driver(raw, side):
    need(side in ('previous-current', 'current'),
         'unknown branch diagnostic helper source side')
    expected = (PREVIOUS_P07_BRANCH_ADVANCE_BLOCK if side == 'previous-current'
                else CURRENT_P07_BRANCH_ADVANCE_BLOCK)
    forbidden = (CURRENT_P07_BRANCH_ADVANCE_BLOCK if side == 'previous-current'
                 else PREVIOUS_P07_BRANCH_ADVANCE_BLOCK)
    expected_raw = expected.encode('utf-8')
    forbidden_raw = forbidden.encode('utf-8')
    sentinel = b'\x00<P07_BRANCH_ADVANCE_DRIVER>\x00'
    need(sentinel not in raw,
         side + ' branch diagnostic helper contains the comparison sentinel')
    need(raw.count(expected_raw) == 1 and raw.count(forbidden_raw) == 0,
         side + ' branch diagnostic helper differs beyond the exact test-driver call/comment block')
    return raw.replace(expected_raw, sentinel)


def validate_previous_current_diagnostic_helper(previous_root, previous_map,
                                                current_root, current_map):
    previous_root = Path(previous_root).resolve(strict=True)
    current_root = Path(current_root).resolve(strict=True)
    need(previous_root.name == PREVIOUS_CURRENT_INPUTS_BASENAME and
         current_root.name == CURRENT_INPUTS_BASENAME and
         previous_root.parent == current_root.parent,
         'previous/current diagnostic sources are outside the adjacent fixed snapshot4/5 paths')
    changed, removed, added = source_map_transition(previous_map, current_map)
    need(changed == [DIAGNOSTIC_HELPER_PATH] and not removed and not added,
         'snapshot4-to-5 input transition differs beyond the single diagnostic test helper')
    need(previous_map.get(DIAGNOSTIC_HELPER_PATH, {}).get('sha256') ==
         PREVIOUS_CURRENT_DIAGNOSTIC_SHA256 and
         current_map.get(DIAGNOSTIC_HELPER_PATH, {}).get('sha256') == CURRENT_DIAGNOSTIC_SHA256,
         'previous/current diagnostic helper source hashes differ from the closed adapter')
    _, previous_raw = source_file(previous_root, previous_map, DIAGNOSTIC_HELPER_PATH,
                                  'snapshot4 diagnostic helper')
    _, current_raw = source_file(current_root, current_map, DIAGNOSTIC_HELPER_PATH,
                                 'snapshot5 diagnostic helper')
    previous_raw.decode('utf-8')
    current_raw.decode('utf-8')
    previous_normalized = normalize_branch_diagnostic_driver(previous_raw, 'previous-current')
    current_normalized = normalize_branch_diagnostic_driver(current_raw, 'current')
    need(previous_normalized == current_normalized,
         'snapshot5 diagnostic helper changed outside the exact branch test-driver call/comment block')

    mutation_canaries = []
    for label, changed_block in [
        ('changed-production-quantum',
         CURRENT_P07_BRANCH_ADVANCE_BLOCK.replace('advanceSlice()', 'advanceSlice(1)')),
        ('changed-call-comment',
         CURRENT_P07_BRANCH_ADVANCE_BLOCK.replace(
             'Keep one counted diagnostic unit on the production router quantum.',
             'Altered diagnostic comment.')),
    ]:
        current_block_raw = CURRENT_P07_BRANCH_ADVANCE_BLOCK.encode('utf-8')
        changed_block_raw = changed_block.encode('utf-8')
        mutated = current_raw.replace(current_block_raw, changed_block_raw, 1)
        need(mutated != current_raw,
             'branch test-driver mutation target differs: ' + label)
        try:
            normalize_branch_diagnostic_driver(mutated, 'current')
        except ValueError:
            mutation_canaries.append(label)
        else:
            raise ValueError('branch test-driver call/comment mutation canary was accepted: ' + label)
    outside_target = b'final int call = ++result.advanceCalls;'
    need(current_raw.count(outside_target) == 1,
         'branch diagnostic outside-block mutation target differs')
    mutated_outside = current_raw.replace(outside_target,
        outside_target + b' // mutation-canary', 1)
    need(normalize_branch_diagnostic_driver(mutated_outside, 'current') != previous_normalized,
         'branch helper outside-block mutation escaped full-source equality')
    mutation_canaries.append('outside-driver-body')
    line_ending_target = b'final int call = ++result.advanceCalls;\n'
    need(current_raw.count(line_ending_target) == 1,
         'branch diagnostic outside-block line-ending target differs')
    changed_line_ending = current_raw.replace(
        line_ending_target, line_ending_target[:-1] + b'\r\n', 1)
    need(normalize_branch_diagnostic_driver(changed_line_ending, 'current') !=
         previous_normalized,
         'branch helper outside-block line-ending mutation escaped byte comparison')
    mutation_canaries.append('outside-driver-line-ending')
    return {
        'previousCurrentSnapshotRevision': PREVIOUS_CURRENT_SOURCE_SNAPSHOT_REVISION,
        'previousCurrentPinSha256': PREVIOUS_CURRENT_PIN_SHA256,
        'currentPinSha256': CURRENT_PIN_SHA256,
        'expectedChangedPaths': [DIAGNOSTIC_HELPER_PATH],
        'actualChangedPaths': changed,
        'retiredPaths': removed,
        'addedPaths': added,
        'previousSourceSha256': sha_bytes(previous_raw),
        'currentSourceSha256': sha_bytes(current_raw),
        'previousSourceBytes': len(previous_raw),
        'currentSourceBytes': len(current_raw),
        'previousCall': 'advanceSlice(2048)',
        'currentCall': 'advanceSlice()',
        'currentComment': 'Keep one counted diagnostic unit on the production router quantum.',
        'driverBlockOccurrences': 1,
        'sameBytesOutsideExactDriverBlock': True,
        'driverMutationCanariesRejected': mutation_canaries,
        'driverMutationCanaryCount': len(mutation_canaries),
    }


def validate_normalizer_chain(roots, maps):
    values = {}
    for name, layout in [('baseline', BASELINE_LAYOUT), ('reference', REFERENCE_LAYOUT),
                         ('current', CURRENT_LAYOUT)]:
        boundary, source = read_context_normalizer(
            roots[name], maps[name], layout, name)
        values[name] = (boundary, source)
    hashes = [values[name][0]['epochNeutralCalledBlockSha256']
              for name in ['baseline', 'reference', 'current']]
    need(len(set(hashes)) == 1,
         'compactness geometry hash helper changed beyond four epoch literals and the named prefix projection')
    current_text = values['current'][1]
    need(current_text.count('return normalized;') == 1,
         'current compactness normalization mutation target differs')
    mutated = current_text.replace('return normalized;', 'return fingerprint;', 1)
    mutated_block, _ = current_layout25_epoch_neutral_normalizer(mutated)
    need(sha_bytes(mutated_block.encode('utf-8')) != hashes[0],
         'compactness helper logic mutation escaped the normalized source-boundary canary')
    projection_mutated = current_text.replace(
        'String projected = "layout=24|" + before.substring(currentPrefix.length());',
        'String projected = "layout=23|" + before.substring(currentPrefix.length());', 1)
    need(projection_mutated != current_text,
         'current prefix projection mutation target differs')
    try:
        current_layout25_epoch_neutral_normalizer(projection_mutated)
    except ValueError:
        projection_mutation_rejected = True
    else:
        raise ValueError('current compactness prefix projection mutation canary was accepted')
    return {
        name: boundary for name, (boundary, _) in values.items()
    } | {'sameHelperAfterFourEpochLiteralsAndApprovedCurrentPrefixProjection': True,
         'helperMutationCanaryRejected': True,
         'currentPrefixProjectionMutationCanaryRejected': projection_mutation_rejected}


def load_native_result(path, source, context, source_map, attempt):
    raw = read_bounded(path, MAX_RESULT_BYTES, source + ' native wrapper result')
    result = strict_json(raw.decode('utf-8-sig'))
    need(type(result) is dict and result.get('schema') ==
         'troubleshootjs-p07-geometry-ordering-stream-result-v1' and
         result.get('source') == source and result.get('suite') == 'P07GeometryEpochDiagnosticContractTest' and
         result.get('layoutVersion') == context['layoutVersion'] and
         result.get('dependencyEpoch') == context['dependencyEpoch'] and
         result.get('inputRevision') == context['inputRevision'] and
         result.get('inputCount') == context['inputCount'] and
         result.get('inputPinsSha256') == context['pinsSha256'] and
         result.get('inputAudit') == 'PASS_BEFORE_AND_AFTER',
         source + ' result does not bind its declared versioned source context')
    need(type(result.get('exitCode')) is int and result['exitCode'] == 0 and
         type(result.get('elapsedSeconds')) in (int, float) and
         0 < result['elapsedSeconds'] <= OUTER_WATCHDOG_MS / 1000 and
         result.get('nativeChildLimitMs') == NATIVE_CHILD_LIMIT_MS and
         result.get('outerWatchdogMs') == OUTER_WATCHDOG_MS and
         result.get('terminationProven') is True and
         result.get('cleanupStatus') in (None, 'PASS_MARKER_REPORTED_ONCE'),
         source + ' bounded native wrapper did not prove exit, cleanup, or limits')
    if 'sourceSnapshotRevision' in result:
        need(result['sourceSnapshotRevision'] == context['sourceSnapshotRevision'],
             source + ' wrapper-reported source snapshot revision differs')
    need(type(result.get('processId')) is int and result['processId'] > 0 and
         type(result.get('processStartTicks')) is int and result['processStartTicks'] > 0 and
         type(result.get('helperElapsedSeconds')) in (int, float) and result['helperElapsedSeconds'] > 0,
         source + ' native process ownership/timing metadata is missing')
    validate_transcript_binding(result, source, attempt)
    module_path = 'scripts/VerifierIsolation.psm1'
    need(module_path in source_map and
         ('moduleSha256' not in result or result['moduleSha256'] == source_map[module_path]['sha256']),
         source + ' bounded helper module digest differs from the sealed source context')
    return result, raw


def validate_current_stdout_binding(result, stdout, console, attempt):
    expected_console = 'geometry-current-' + str(attempt) + '-console.txt'
    expected_stdout = 'geometry-current-' + str(attempt) + '-stdout.txt'
    need(result.get('consoleFile') == expected_console and
         result.get('stdoutFile') == expected_stdout and
         type(result.get('consoleBytes')) is int and result['consoleBytes'] == len(console) and
         result.get('consoleSha256') == sha_bytes(console) and
         type(result.get('stdoutBytes')) is int and result['stdoutBytes'] == len(stdout) and
         result.get('stdoutSha256') == sha_bytes(stdout) and stdout == console,
         'current console is not byte-identical to the bounded helper stdout')
    return True


def current_stdout_binding_canaries(attempt):
    stdout = b'bounded helper stdout fixture\n'
    result = {
        'consoleFile': 'geometry-current-' + str(attempt) + '-console.txt',
        'stdoutFile': 'geometry-current-' + str(attempt) + '-stdout.txt',
        'consoleBytes': len(stdout), 'consoleSha256': sha_bytes(stdout),
        'stdoutBytes': len(stdout), 'stdoutSha256': sha_bytes(stdout),
    }
    need(validate_current_stdout_binding(result, stdout, stdout, attempt),
         'matching current console/stdout fixture was rejected')
    mutations = [
        ('wrong-console-basename', {'consoleFile': 'geometry-current-999-console.txt'}),
        ('wrong-stdout-basename', {'stdoutFile': 'geometry-current-999-stdout.txt'}),
        ('wrong-console-byte-count', {'consoleBytes': len(stdout) + 1}),
        ('wrong-console-digest', {'consoleSha256': '0' * 64}),
        ('wrong-stdout-byte-count', {'stdoutBytes': len(stdout) + 1}),
        ('wrong-stdout-digest', {'stdoutSha256': '0' * 64}),
    ]
    rejected = []
    for label, changes in mutations:
        candidate = dict(result)
        candidate.update(changes)
        try:
            validate_current_stdout_binding(candidate, stdout, stdout, attempt)
        except ValueError:
            rejected.append(label)
        else:
            raise ValueError('current stdout binding negative canary accepted: ' + label)
    try:
        validate_current_stdout_binding(result, stdout, stdout + b'extra', attempt)
    except ValueError:
        rejected.append('console-stdout-byte-mismatch')
    else:
        raise ValueError('current stdout byte-mismatch canary was accepted')
    return {'positiveCount': 1, 'negativeCount': len(rejected),
            'negativeRejected': rejected}


def validate_console_suffix(core, lines, layout, source, attempt):
    footer_indexes = [index for index, line in enumerate(lines) if line.startswith(core.FOOTER)]
    need(len(footer_indexes) == 1, source + ' console must contain exactly one native footer')
    footer_index = footer_indexes[0]
    observation = core.extract(lines[:footer_index + 1], layout)
    raw_suffix = lines[footer_index + 1:]
    suffix = [line.strip() for line in raw_suffix if line.strip()]
    expected_transcript = 'geometry-' + source + '-' + str(attempt) + '.log'
    need(lines.count(CLEANUP_LINE) == 1 and len(suffix) >= 2 and
         suffix[0] == PASS_LINE and suffix[1] == CLEANUP_LINE,
         source + ' console lacks the exact focused PASS/cleanup suffix')
    if source == 'current':
        need(len(suffix) == 2 and all(line == line.strip() for line in raw_suffix if line.strip()),
             source + ' captured stdout must end after the native PASS/cleanup markers')
        return observation, 'captured-stdout-bare-footer'
    if len(suffix) == 3 and suffix[2].startswith(TRANSCRIPT_PREFIX):
        transcript = suffix[2][len(TRANSCRIPT_PREFIX):]
        need(bool(transcript) and transcript == transcript.strip() and
             geometry.ntpath.basename(transcript) == expected_transcript,
             source + ' legacy transcript path has the wrong source/attempt basename')
        return observation, 'legacy-transcript-path'
    need(len(suffix) == 6 and suffix[2] == TRANSCRIPT_STARS and
         suffix[3] == PS_TRANSCRIPT_END and re.fullmatch(r'End time: [0-9]{14}', suffix[4]) is not None and
         suffix[5] == TRANSCRIPT_STARS,
         source + ' PowerShell transcript suffix is malformed or contains unexpected lines')
    return observation, 'powershell-transcript-end'


def read_geometry_observation(core, receipt_path, result, console_path,
                              context, source, attempt, stdout_path=None):
    receipt = read_bounded(receipt_path, MAX_NATIVE_BYTES, source + ' native receipt')
    need(result.get('receiptBytes') == len(receipt) and result.get('receiptSha256') == sha_bytes(receipt),
         source + ' receipt bytes/digest differ from maintained result')
    receipt_observation = core.extract(receipt.decode('utf-8-sig').splitlines(), context['layoutVersion'])
    console = read_bounded(console_path, MAX_NATIVE_BYTES, source + ' native console')
    need(result.get('consoleBytes') == len(console) and result.get('consoleSha256') == sha_bytes(console),
         source + ' console bytes/digest differ from maintained result')
    if source == 'current':
        need(stdout_path is not None, 'current geometry observation requires the captured stdout file')
        stdout = read_bounded(stdout_path, MAX_NATIVE_BYTES, source + ' captured stdout')
        validate_current_stdout_binding(result, stdout, console, attempt)
        stdout_checks = current_stdout_binding_canaries(attempt)
    else:
        stdout_checks = None
    console_lines = console.decode('utf-8-sig').splitlines()
    console_observation, envelope = validate_console_suffix(
        core, console_lines, context['layoutVersion'], source, attempt)
    need(console_observation == receipt_observation,
         source + ' console prefix observation differs from the receipt')
    need(result['elapsedSeconds'] * 1000 >= receipt_observation['summary']['nativeDiagnosticElapsedMs'],
         source + ' wrapper elapsed time is shorter than the native observation')
    # Keep arbitrary wrapper fields (which may contain machine-local paths)
    # out of the portable result. The original result bytes remain hash-bound.
    result_fields = [
        'schema', 'source', 'suite', 'layoutVersion', 'dependencyEpoch',
        'inputRevision', 'sourceSnapshotRevision', 'inputCount', 'inputPinsSha256',
        'inputAudit', 'exitCode', 'elapsedSeconds', 'nativeChildLimitMs',
        'outerWatchdogMs', 'terminationProven', 'cleanupStatus', 'processId',
        'processStartTicks', 'helperElapsedSeconds', 'transcriptFile',
        'receiptBytes', 'receiptSha256', 'consoleBytes', 'consoleSha256',
        'stdoutFile', 'stdoutBytes', 'stdoutSha256', 'stderrBytes', 'stderrSha256', 'moduleSha256',
    ]
    safe_result = {key: result[key] for key in result_fields if key in result}
    observation = {
        **receipt_observation,
        'result': safe_result,
        'receiptSha256': sha_bytes(receipt), 'receiptBytes': len(receipt),
        'consoleSha256': sha_bytes(console), 'consoleBytes': len(console),
        'cleanupStatus': 'PASS', 'consoleEnvelope': envelope,
        'stdoutBindingCanaries': stdout_checks,
    }
    return observation, console_lines


def wrapper_canaries(core, actual_observation, lines, context, source, attempt):
    # Canaries use only the captured native prefix; suffix fixtures stay in memory.
    expected = 'geometry-' + source + '-' + str(attempt) + '.log'
    footer_indexes = [index for index, line in enumerate(lines) if line.startswith(core.FOOTER)]
    need(len(footer_indexes) == 1, source + ' captured console footer is not unique in envelope canaries')
    footer_index = footer_indexes[0]
    prefix = lines[:footer_index + 1]
    actual, actual_format = validate_console_suffix(core, lines, context['layoutVersion'], source, attempt)
    need(actual == {key: actual_observation[key] for key in ['frame', 'cases', 'summary', 'footer']} and
         actual_format == actual_observation['consoleEnvelope'],
         source + ' captured console envelope differs from its receipt observation')
    pass_cleanup = [PASS_LINE, CLEANUP_LINE]
    legacy = pass_cleanup + [TRANSCRIPT_PREFIX + 'C:\\reader-canary\\' + expected]
    ps = pass_cleanup + [TRANSCRIPT_STARS, PS_TRANSCRIPT_END,
                         'End time: 20261031042641', TRANSCRIPT_STARS]
    if source == 'current':
        positive_suffixes = [
            ('captured-stdout-bare-footer', pass_cleanup),
            ('blank-spaced-captured-stdout', ['', PASS_LINE, '', CLEANUP_LINE, '', '   ']),
        ]
    else:
        positive_suffixes = [
            ('legacy-transcript-path', legacy),
            ('powershell-transcript-end', ps),
            ('blank-spaced-legacy', ['', PASS_LINE, '', CLEANUP_LINE, '', legacy[2], '', '   ']),
            ('blank-spaced-powershell', ['', PASS_LINE, '', CLEANUP_LINE, '', *ps[2:], '   ']),
        ]
    positives = ['captured-' + actual_format]
    for label, suffix in positive_suffixes:
        parsed, parsed_format = validate_console_suffix(
            core, prefix + suffix, context['layoutVersion'], source, attempt)
        expected_format = ('captured-stdout-bare-footer' if source == 'current' else
                           'legacy-transcript-path' if 'legacy' in label else
                           'powershell-transcript-end')
        need(parsed == actual and parsed_format == expected_format,
            source + ' positive wrapper suffix canary changed native observation: ' + label)
        positives.append(label)
    if source == 'current':
        mutations = [
            ('unexpected-failure-line', prefix + pass_cleanup + ['FAIL: synthetic wrapper failure']),
            ('unexpected-transcript-line', prefix + pass_cleanup +
             [TRANSCRIPT_PREFIX + expected]),
            ('missing-cleanup', prefix + [PASS_LINE]),
            ('extra-trailing-text', prefix + pass_cleanup + ['trailing content']),
            ('empty-suffix', prefix),
            ('duplicate-native-footer', prefix + [lines[footer_index]] + pass_cleanup),
        ]
    else:
        mutations = [
            ('unexpected-failure-line', prefix + ps + ['FAIL: synthetic wrapper failure']),
            ('missing-cleanup', prefix + [PASS_LINE] + ps[2:]),
            ('missing-transcript-end', prefix + ps[:3] + ps[4:]),
            ('extra-trailing-text', prefix + ps + ['trailing content']),
            ('wrong-transcript-basename', prefix + pass_cleanup + [TRANSCRIPT_PREFIX + 'geometry-other-99.log']),
            ('malformed-end-time', prefix + ps[:4] + ['End time: 2026103104264'] + ps[5:]),
            ('empty-suffix', prefix),
            ('duplicate-native-footer', prefix + [lines[footer_index]] + ps),
        ]
    rejected = []
    for label, candidate in mutations:
        try:
            validate_console_suffix(core, candidate, context['layoutVersion'], source, attempt)
        except ValueError:
            rejected.append(label)
        else:
            raise ValueError('geometry wrapper suffix negative canary accepted: ' + label)
    wrong_transcript_result = {'transcriptFile': 'geometry-other-' + str(attempt) + '.log'}
    try:
        validate_transcript_binding(wrong_transcript_result, source, attempt)
    except ValueError:
        rejected.append('wrong-result-transcript-basename')
    else:
        raise ValueError('geometry result transcript-name negative canary accepted')
    return {'positiveAccepted': positives, 'positiveCount': len(positives),
            'negativeCount': len(rejected), 'negativeRejected': rejected}


def validate_transcript_binding(result, source, attempt):
    expected = 'geometry-' + source + '-' + str(attempt) + '.log'
    need(type(result.get('transcriptFile')) is str and result['transcriptFile'] == expected,
         source + ' transcript basename is not bound to the explicit attempt')
    return expected


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-context', type=Path, required=True)
    parser.add_argument('--source-context-sha256', required=True)
    for name in [
        'baseline-pins', 'reference-pins', 'current-pins',
        'previous-current-pins', 'previous-current-inputs',
        'baseline-inputs', 'reference-inputs', 'current-inputs',
        'baseline-post-pins', 'current-post-pins',
        'baseline-normalizer', 'reference-normalizer', 'current-normalizer',
        'baseline-receipt', 'baseline-result', 'baseline-console',
        'current-receipt', 'current-result', 'current-console', 'current-stdout', 'output',
    ]:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--baseline-attempt', type=int, required=True)
    parser.add_argument('--current-attempt', type=int, required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    need(not args.output.exists(), 'refusing to overwrite an existing final-source geometry output')
    load_pinned_helpers()
    context, context_raw, context_sha = load_source_context(
        args.source_context, args.source_context_sha256)
    contexts = context['contexts']
    paths = {'baseline': (args.baseline_pins, args.baseline_inputs),
             'reference': (args.reference_pins, args.reference_inputs),
             'current': (args.current_pins, args.current_inputs)}
    maps, manifests, trees, roots = {}, {}, {}, {}
    for side in ['baseline', 'reference', 'current']:
        maps[side], manifests[side] = load_pin_manifest(paths[side][0], contexts[side], side)
        trees[side] = audit_source_tree(paths[side][1], maps[side], side)
        roots[side] = Path(paths[side][1]).resolve(strict=True)
    transitions = validate_source_transitions(context, maps)
    previous_current_map, previous_current_root = load_previous_current_source_inputs(
        args.previous_current_pins, args.previous_current_inputs)
    previous_current_diagnostic = validate_previous_current_diagnostic_helper(
        previous_current_root, previous_current_map, roots['current'], maps['current'])

    # Bind the post-run baseline/current manifests byte-for-byte to their source contexts.
    baseline_post = read_bounded(args.baseline_post_pins, MAX_PIN_BYTES, 'baseline post-run input manifest')
    current_post = read_bounded(args.current_post_pins, MAX_PIN_BYTES, 'current post-run input manifest')
    need(baseline_post == manifests['baseline'] and current_post == manifests['current'],
         'post-run source manifests differ from their frozen pre-run contexts')

    # Resolve normalizers under each of the three attested source roots.
    normalizer_paths = {'baseline': args.baseline_normalizer,
                        'reference': args.reference_normalizer,
                        'current': args.current_normalizer}
    normalizers = {}
    for side in ['baseline', 'reference', 'current']:
        expected_path = 'tests/contracts/PcbCompactnessContractTest.java'
        need(normalizer_paths[side].resolve(strict=True) ==
             (roots[side] / Path(*expected_path.split('/'))).resolve(strict=True),
             side + ' normalizer is not the exact pinned source path')
    normalizers = validate_normalizer_chain(roots, maps)
    diagnostic = validate_diagnostic_source_chain(roots, maps)
    compactness = validate_compactness_hashes(maps, roots)

    baseline_result, baseline_result_raw = load_native_result(
        args.baseline_result, 'baseline', contexts['baseline'], maps['baseline'], args.baseline_attempt)
    current_result, current_result_raw = load_native_result(
        args.current_result, 'current', contexts['current'], maps['current'], args.current_attempt)
    baseline, baseline_console_lines = read_geometry_observation(
        geometry, args.baseline_receipt, baseline_result, args.baseline_console,
        contexts['baseline'], 'baseline', args.baseline_attempt)
    current, current_console_lines = read_geometry_observation(
        geometry, args.current_receipt, current_result, args.current_console,
        contexts['current'], 'current', args.current_attempt, args.current_stdout)

    expected_current_compact = Q_COMPACT2_EXPECTED
    compact_cases = {row['rootSeed']: row['normalizedPhysicalSha256']
                     for row in current['cases'] if row['placementInterpretationVersion'] == 2}
    need(compact_cases == expected_current_compact,
         'layout25 compact2 native observations differ from source-bound Q physical hashes')
    legacy1_cases = {row['rootSeed']: row['normalizedPhysicalSha256']
                     for row in current['cases'] if row['placementInterpretationVersion'] == 1}
    need(legacy1_cases == Q3_LAYOUT25_LEGACY1_EXPECTED,
         'layout25 legacy1 native observations differ from the source-bound Q3 physical hashes')

    comparisons = geometry.compare(baseline, current)
    geometry_canaries = (geometry.negative_canaries(baseline, BASELINE_LAYOUT) +
                         geometry.negative_canaries(current, CURRENT_LAYOUT))
    envelope_checks = {
        'baseline': wrapper_canaries(geometry, baseline, baseline_console_lines, contexts['baseline'],
                                     'baseline', args.baseline_attempt),
        'current': wrapper_canaries(geometry, current, current_console_lines, contexts['current'],
                                    'current', args.current_attempt),
    }
    result = {
        'protocol': PROTOCOL,
        'status': 'PASS_COMPARISON_ONLY',
        'scope': 'FRESH_FINAL_SOURCE_SIX_BOARD_LAYOUT22_TO_LAYOUT25_COMPARISON',
        'layoutQualification': False,
        'adopted': False,
        'automaticGoldenSeal': False,
        'coreMainInvoked': False,
        'coreConstantsPatched': False,
        'baseline': baseline,
        'current': current,
        'baselineLayoutVersion': BASELINE_LAYOUT,
        'referenceLayoutVersion': REFERENCE_LAYOUT,
        'currentLayoutVersion': CURRENT_LAYOUT,
        'baselineDependencyEpoch': 'tsj-generation-dependencies-v23',
        'referenceDependencyEpoch': 'tsj-generation-dependencies-v25',
        'currentDependencyEpoch': 'tsj-generation-dependencies-v26',
        'sourceContext': {
            'candidateId': context['candidateId'],
            'contextSha256': context_sha,
            'contexts': {
                side: {
                    'contextId': contexts[side]['contextId'],
                    'layoutVersion': contexts[side]['layoutVersion'],
                    'dependencyEpoch': contexts[side]['dependencyEpoch'],
                    'inputRevision': contexts[side]['inputRevision'],
                    'sourceSnapshotRevision': contexts[side]['sourceSnapshotRevision'],
                    'inputCount': contexts[side]['inputCount'],
                    'pinsSha256': contexts[side]['pinsSha256'],
                }
                for side in ['baseline', 'reference', 'current']
            },
            'baselinePinsSha256': sha_bytes(manifests['baseline']),
            'qualifiedReferencePinsSha256': sha_bytes(manifests['reference']),
            'currentPinsSha256': sha_bytes(manifests['current']),
            'baselineInputCount': len(maps['baseline']),
            'qualifiedReferenceInputCount': len(maps['reference']),
            'currentInputCount': len(maps['current']),
            'transitions': transitions,
            'trees': trees,
            'postRunManifestsMatch': True,
        },
        'qualifiedReferenceEvidenceSha256': context['referenceQualification'],
        'sourceBoundaries': {
            'compactnessNormalizer': normalizers,
            'diagnosticEmitter': diagnostic,
            'previousCurrentDiagnosticHelper': previous_current_diagnostic,
            'compactnessCurrentHashSource': compactness,
        },
        'comparisons': comparisons,
        'geometryNegativeCanaries': geometry_canaries,
        'wrapperEnvelopeCanaries': envelope_checks,
        'currentStdoutBindingCanaries': current['stdoutBindingCanaries'],
        'sameLogicalDeclarationsAndStreams': all(
            row['logicalDeclarationsAndStreamsUnchanged'] for row in comparisons),
        'currentCompact2HashesMatchAuditedQ': True,
        'currentLegacy1HashesMatchSourceBoundQ3': True,
        'currentLegacy1Q3Evidence': {
            'geometryComparisonSha256': Q3_GEOMETRY_COMPARISON_SHA256,
            'geometryReaderSha256': Q3_GEOMETRY_READER_SHA256,
            'sourcePinManifestSha256': Q3_GEOMETRY_PIN_SHA256,
        },
        'nativeResults': {
            'baselineResultSha256': sha_bytes(baseline_result_raw),
            'baselineResultBytes': len(baseline_result_raw),
            'currentResultSha256': sha_bytes(current_result_raw),
            'currentResultBytes': len(current_result_raw),
        },
        'limits': [
            'One frozen baseline source and the previously qualified AN source context are compared with one final25/26 context.',
            'Six fixed geometry cases only; the 72 saved-profile comparison is a separate required reader.',
            'This result does not qualify general layout admission, production timing, normal-player repair, or playable100.',
            'The previous reference evidence hashes are context bindings; final-source acceptance still requires the separate native/build/runtime gates.',
            'No golden is updated automatically; current compact2 hashes are checked against the audited Q values and the pinned final Java source.',
        ],
    }
    result['readerSha256'] = sha_bytes(Path(__file__).read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2, ensure_ascii=True, allow_nan=False) + '\n')
    print('PASS: final-source six-board comparison only; layout qualification=false; adoption=false')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
