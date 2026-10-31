"""Audit the six fixed native geometry observations across layout22 -> layout24."""
import argparse
import collections
import copy
import hashlib
import json
import ntpath
import re
from pathlib import Path
from read_diagnostic import strict_json, need, integer

SCOPE = 'OBSERVATIONAL_SIX_BOARD_EPOCH_TRANSITION'
FRAME = 'P07_GEOMETRY_EPOCH_DIAGNOSTIC_FRAME '
CASE = 'P07_GEOMETRY_EPOCH_CASE '
SUMMARY = 'P07_GEOMETRY_EPOCH_DIAGNOSTIC_SUMMARY '
FOOTER = 'PASS: P07 geometry epoch diagnostic contracts '
ROOTS = [('-1017', 40, 96), ('-1004', 44, 104), ('-1022', 60, 143)]
OLD = [
    '27f9389815a080fc6d50890f868585b5e0ed7bd14f75644c041dff948d6d1f1a',
    '582064e2986b77ff92348024814f01fc0581c8f2273bd37ca695a26a3008574d',
    '17b1b0d0452d64332d636c46833b2be44348380303a8b66f539216ecac59f7d4',
    '279e83fea450e4b4be6767a56bf2ba748bba5c6dd7817f60b07f48ff6906c378',
    '15bb054ccc9a57f1dc111800127901b90d829d36fec5886ea4212e3f38b8b328',
    'f42fbd62199a8b254964cd256f7e45ebf4b9b6d06eef3ad89911ed8ecb32f375']
BASELINE_PIN_FILE_SHA256 = '09ac49088fef03a2a0aee0929647f0e16b14bae15573faa89e71e19ad7d525da'
CURRENT_PIN_FILE_SHA256 = 'f7eea4e90710a8fb958fbf5ca039367c0abfaf6d5385dd529eae81e1eb9a6717'
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
UNCHANGED_ORACLE_INPUTS = [
    'tests/contracts/P08ReferenceConductorBuilder.java',
    'tests/contracts/P08ScalabilityContractTest.java',
    'tests/contracts/P07LayeredRasterOracle.java',
    'tests/contracts/PcbFunctionalGroupingContractTest.java',
    'tests/contracts/PcbPocketPackingContractTest.java',
    'scripts/VerifierIsolation.psm1',
    '.tools/gwt-2.7.0/gwt-user-2.7.0.jar',
    '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar',
]
DIAGNOSTIC_SOURCE_PATH = 'tests/contracts/P07GeometryEpochDiagnosticContractTest.java'
DIAGNOSTIC_ADAPTERS = [
    ('current-layout-guard', 'layoutVersion == 23', 'layoutVersion == 24'),
    ('current-dependency-epoch', 'tsj-generation-dependencies-v24',
     'tsj-generation-dependencies-v25'),
    ('current-epoch-error-label', 'layout23/dependency24', 'layout24/dependency25'),
]
DIAGNOSTIC_ADAPTER_OCCURRENCES = 2


def sha(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def normalized_geometry(row):
    raw = row['layoutGeometryFingerprint']
    prefix = 'layout=' + str(row['layoutVersion']) + '|'
    need(type(raw) is str and raw.startswith(prefix) and len(raw) <= 1048576,
         'incomplete bounded geometry fingerprint')
    references = set()
    def replace(match):
        component = match[1]
        need(component not in references, 'duplicate component reference')
        references.add(component)
        return 'L:component:' + component + '@' + match[2] + ':<REF_X>,<REF_Y>,' + match[5] + ',' + match[6] + ';'
    normalized = re.sub(r'L:component:([^@;]+)@([^:;]+):(-?\d+),(-?\d+),(\d+),(\d+);',
                        replace, 'layout=<LAYOUT_EPOCH>|' + raw[len(prefix):])
    need(references == {item['componentId'] for item in row['componentMappings']},
         'reference normalization did not cover the exact component census')
    need(sha(raw) == row['layoutGeometrySha256'] and sha(normalized) == row['normalizedPhysicalSha256'],
         'independent full/normalized geometry hash differs')
    return normalized


def traces(text):
    need(type(text) is str and 0 < len(text) <= 1048576, 'missing bounded complete copper text')
    result, offset, sources = [], 0, set()
    while offset < len(text):
        length = re.match(r'(\d+):', text[offset:])
        need(length is not None, 'unknown trace-source framing')
        offset += len(length[0])
        size = int(length[1])
        source = text[offset:offset + size]
        need(0 < size <= 1024 and source.isascii() and source not in sources, 'duplicate/incomplete trace source')
        sources.add(source)
        offset += size
        header = re.match(r'(TOP|BOTTOM):(EXPOSED|COVERED):([^:;]+):', text[offset:])
        need(header is not None, 'unknown layer/exposure/net framing')
        offset += len(header[0])
        points = []
        while True:
            point = re.match(r'(-?\d+),(-?\d+);', text[offset:])
            if point is None:
                break
            points.append((int(point[1]), int(point[2])))
            offset += len(point[0])
        need(len(points) >= 2 and all((a[0] == b[0]) != (a[1] == b[1])
             for a, b in zip(points, points[1:])), 'empty/non-Manhattan copper segment')
        result.append({'source': source, 'layer': header[1], 'net': header[3], 'points': points})
    return result


def validate_row(row, index, layout):
    seed, packages, pads = ROOTS[index % 3]
    interpretation = 1 + index // 3
    need(row.get('status') == 'PASS_OBSERVATION_ONLY' and row.get('scope') == SCOPE and
         type(row.get('frameId')) is str and 0 < len(row['frameId']) <= 256 and
         row.get('layoutQualification') is False and row.get('adopted') is False and
         row.get('rootSeed') == seed and integer(row.get('placementInterpretationVersion'), interpretation, interpretation) and
         integer(row.get('layoutVersion'), layout, layout) and
         row.get('dependencyEpoch') == 'tsj-generation-dependencies-v' + str(layout + 1) and
         integer(row.get('planVersion'), 1, 1) and
         row.get('modelDumpEpoch') == 'circuitjs-source-load-model-inputs-no-transient-dump-v7' and
         integer(row.get('expectedPackages'), packages, packages) and integer(row.get('expectedPads'), pads, pads) and
         row.get('baselineExpected') == OLD[index], 'case identity/count/baseline differs')
    for key in ['tracePolylineCount', 'traceSegmentCount', 'p08SourceSegmentCount', 'p08SourceLandCount',
                'p08SegmentPairs', 'p08SegmentLandPairs', 'p08LandPairs', 'p08OrderedPadPairs',
                'layeredRasterPoints', 'layeredOrderedPadPairs', 'layeredTraceUnitSteps', 'layeredPlatedPadBarrels',
                'layeredViaBarrels', 'layeredPlatedHoleBarrels', 'layeredPackageInternalLinks',
                'productionClearanceExactPairs', 'productionClearancePotentialPairs', 'referenceClearancePairs',
                'platedHoleCount', 'nonPlatedHoleCount', 'layoutWidth', 'layoutHeight', 'selectedPlacementAttempt', 'boardArea']:
        need(integer(row.get(key), 0, 1000000000000), 'invalid typed observation counter: ' + key)
    need(row.get('physicalPolicyId') == 'MEDIUM_BOARD' and row.get('physicalPolicyVersion') == 1 and
         row.get('traceWidthProfile') == 'UNIFORM_TRACE@1' and row.get('routeAccepted') is True and
         row.get('selectedRoutePolicy') == 'P07_FULLER_TWO_LAYER', 'actual policy/route differs')
    for key in ['layoutGeometryValidation', 'twoLayerRulesValidation', 'productionFiniteWidthClearance',
                'independentP08Partition', 'layeredCenterline']:
        need(row.get(key) == 'PASS', 'missing native independent/physical witness: ' + key)
    need(row.get('normalAdmission') == row.get('nativePrivacy') == 'NOT CLAIMED' and
         row.get('timingScope') == 'NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING', 'native scope inflated')
    components = row.get('componentMappings')
    need(type(components) is list and len(components) == packages and
         len({item['componentId'] for item in components}) == packages, 'component mapping census differs')
    terminals = [pad for component in components for pad in component['terminals']]
    need(len(terminals) == len({pad['padId'] for pad in terminals}) == pads and
         all(pad['netId'] == pad['declaredNetId'] and pad['attachment'] == 'PLATED_THROUGH_HOLE' for pad in terminals),
         'physical terminal identity/attachment differs')
    nets = {pad['netId'] for pad in terminals}
    need(type(row.get('declaredNets')) is list and row['declaredNets'] == sorted(nets) and
         all(row.get(key) == pads * pads for key in ['p08OrderedPadPairs', 'layeredOrderedPadPairs']) and
         row.get('layeredPlatedPadBarrels') == pads, 'ordered pad partition/barrel census differs')
    normalized_geometry(row)
    need(type(row.get('p08Canonical')) is str and sha(row['p08Canonical']) == row.get('p08CanonicalSha256'),
         'incomplete independent graph canonical')
    copper = traces(row['traceGeometryFingerprint'])
    need(len(copper) == row.get('tracePolylineCount') and
         dict(collections.Counter(item['layer'] for item in copper)) == row.get('traceCountsByLayer') and
         all(item['net'] in nets for item in copper), 'copper inventory differs')
    raw_segments = sum(len(item['points']) - 1 for item in copper)
    normalized_segments = 0
    for item in copper:
        path = []
        for point in item['points']:
            if path and path[-1] == point:
                continue
            if len(path) >= 2:
                a, b = path[-2:]
                if ((a[0] == b[0] == point[0] and (b[1] - a[1]) * (point[1] - b[1]) > 0) or
                    (a[1] == b[1] == point[1] and (b[0] - a[0]) * (point[0] - b[0]) > 0)):
                    path.pop()
            path.append(point)
        normalized_segments += len(path) - 1
    holes = row.get('holes')
    need(type(holes) is list and len({hole['id'] for hole in holes}) == len(holes), 'hole census differs')
    kinds = collections.Counter(hole['kind'] for hole in holes)
    need(all(kind in ['VIA', 'PLATED', 'NON_PLATED'] for kind in kinds) and
         dict(kinds) == row.get('holeCountsByKind') and integer(row.get('viaCount'), 0, 48) and
         all(row.get(key) == kinds[kind] for key, kind in
             [('viaCount', 'VIA'), ('platedHoleCount', 'PLATED'), ('nonPlatedHoleCount', 'NON_PLATED')]),
         'hole type/limit conservation differs')
    lands = 2 * (pads + kinds['VIA'] + kinds['PLATED'])
    need(row.get('traceSegmentCount') == raw_segments and row.get('p08SourceSegmentCount') == normalized_segments and
         row.get('p08SourceLandCount') == lands and row.get('p08SegmentPairs') == normalized_segments * (normalized_segments - 1) // 2 and
         row.get('p08SegmentLandPairs') == normalized_segments * lands and row.get('p08LandPairs') == lands * (lands - 1) // 2,
         'independent source/land/all-pairs census differs')
    clearance_pairs = sum((len(a['points']) - 1) * (len(b['points']) - 1)
        for position, a in enumerate(copper) for b in copper[position + 1:]
        if a['layer'] == b['layer'] and a['net'] != b['net'])
    need(row.get('referenceClearancePairs') == clearance_pairs and row.get('clearanceMinimumCenterlineRule') == 15,
         'independent uniform-width clearance census differs')
    if layout == 22:
        need(row['normalizedPhysicalSha256'] == OLD[index], 'frozen source did not reproduce the old oracle')


def extract(lines, layout, console=False):
    entries = [(kind, strict_json(line[len(prefix):])) for line in lines for kind, prefix in
               [('frame', FRAME), ('case', CASE), ('summary', SUMMARY)] if line.startswith(prefix)]
    need([kind for kind, _ in entries] == ['frame'] + ['case'] * 6 + ['summary'], 'six-board invocation framing differs')
    frame, summary = entries[0][1], entries[-1][1]
    rows = [value for _, value in entries[1:-1]]
    run = frame.get('runId')
    need(type(run) is str and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', run) is not None,
         'missing native invocation UUID')
    for item in [frame, summary] + rows:
        need(item.get('runId') == run and item.get('layoutVersion') == layout and
             item.get('dependencyEpoch') == 'tsj-generation-dependencies-v' + str(layout + 1) and
             item.get('scope') == SCOPE and item.get('layoutQualification') is False and item.get('adopted') is False,
             'mixed source/invocation/scope metadata')
    need(frame.get('caseCount') == summary.get('cases') == 6 and
         len({row.get('frameId') for row in rows}) == 6 and
         integer(summary.get('assertions'), 80962, 10000000), 'native six-case census differs')
    footer = [(index, line) for index, line in enumerate(lines) if line.startswith(FOOTER)]
    summaries = [index for index, line in enumerate(lines) if line.startswith(SUMMARY)]
    need(len(footer) == 1 and footer[0][0] > summaries[0], 'missing/reordered terminal native pass')
    expected_footer = (FOOTER + 'assertions=' + str(summary['assertions']) + ' cases=6 layout=' + str(layout) +
        ' dependency=tsj-generation-dependencies-v' + str(layout + 1) + ' scope=' + SCOPE)
    need(footer[0][1] == expected_footer and
         integer(summary.get('nativeDiagnosticElapsedMs'), 0, 180000) and
         summary.get('timingScope') == 'NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING', 'terminal native counters/timing differ')
    suffix = [line.strip() for line in lines[footer[0][0] + 1:] if line.strip()]
    if console:
        transcript_prefix = 'Transcript stopped, output file is '
        need(len(suffix) == 3 and suffix[0] ==
             'PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.' and
             suffix[1] == 'CLEANUP: current JVM contract classes and task-owned scratch removed.' and
             suffix[2].startswith(transcript_prefix) and
             re.fullmatch(r'geometry-(?:baseline|current)-\d+\.log',
                          ntpath.basename(suffix[2][len(transcript_prefix):])) is not None,
             'unexpected wrapper/failure content after native pass')
    else:
        need(not suffix, 'receipt has trailing content after native pass')
    for index, row in enumerate(rows):
        validate_row(row, index, layout)
        need(integer(row.get('nativeDiagnosticElapsedMs'), 0, 180000), 'invalid native case elapsed time')
    need(sum(row['nativeDiagnosticElapsedMs'] for row in rows) <= summary['nativeDiagnosticElapsedMs'],
         'case timing exceeds the native observation interval')
    return {'frame': frame, 'cases': rows, 'summary': summary, 'footer': footer[0][1]}


def read_observation(path, result_path, console_path, layout):
    raw = path.read_bytes()
    need(len(raw) <= 8 * 1024 * 1024, 'six-board receipt exceeds transport bound')
    observation = extract(raw.decode('utf-8-sig').splitlines(), layout)
    console = console_path.read_bytes()
    need(len(console) <= 8 * 1024 * 1024, 'native console exceeds transport bound')
    console_lines = console.decode('utf-8-sig').splitlines()
    need(extract(console_lines, layout, console=True) == observation and
         console_lines.count('CLEANUP: current JVM contract classes and task-owned scratch removed.') == 1,
         'receipt/console invocation or explicit cleanup differs')
    result = strict_json(result_path.read_text(encoding='utf-8-sig'))
    need(type(result) is dict and integer(result.get('exitCode'), 0, 0) and
         type(result.get('elapsedSeconds')) in [int, float] and 0 < result['elapsedSeconds'] <= 600 and
         result['elapsedSeconds'] * 1000 >= observation['summary']['nativeDiagnosticElapsedMs'],
         'native helper did not fully succeed or has inconsistent elapsed time')
    return dict(observation, result=result, receiptSha256=hashlib.sha256(raw).hexdigest(), receiptBytes=len(raw),
                consoleSha256=hashlib.sha256(console).hexdigest(), cleanupStatus='PASS')


def source_snapshot_root(normalizer_path, side):
    path = Path(normalizer_path).resolve(strict=True)
    need(path.is_file() and path.name == 'PcbCompactnessContractTest.java',
         side + ' normalizer path is not the pinned compactness helper')
    root = path.parents[2]
    need(path.relative_to(root).as_posix() ==
         'tests/contracts/PcbCompactnessContractTest.java',
         side + ' normalizer does not have the expected snapshot-relative path')
    return root


def pins(path):
    rows = strict_json(path.read_text(encoding='utf-8-sig'))
    need(type(rows) is list and len(rows) >= 60, 'missing compiler input census')
    result = {}
    for row in rows:
        key = row['path'].replace('\\', '/')
        need(key not in result and not key.startswith('/') and re.match(r'^[A-Za-z]:', key) is None and
             '..' not in key.split('/') and
             re.fullmatch('[0-9a-f]{64}', row['sha256']) is not None, 'invalid/duplicate source pin')
        result[key] = row['sha256']
    return result


def epoch_neutral_normalizer(text, layout):
    text = text.replace('\r\n', '\n')
    start, end = 'static String physicalGeometrySha256(', 'private static void verifyPhysicalOracleNormalization('
    need(text.count(start) == text.count(end) == 1, 'normalization helper framing differs')
    block = text[text.index(start):text.index(end)]
    substitutions = [
        ('CURRENT_VERSION != ' + str(layout), 'CURRENT_VERSION != <EPOCH>'),
        ('Physical geometry oracle requires layout version ' + str(layout), 'Physical geometry oracle requires layout version <EPOCH>'),
        ('"layout=' + str(layout) + '|"', '"layout=<EPOCH>|"'),
        ('Expected actual layout=' + str(layout) + ' geometry fingerprint', 'Expected actual layout=<EPOCH> geometry fingerprint')]
    for before, after in substitutions:
        need(block.count(before) == 1, 'unexpected normalization epoch literal')
        block = block.replace(before, after)
    return block


def normalizer_boundary(path, expected_hash, layout):
    raw = path.read_bytes()
    need(hashlib.sha256(raw).hexdigest() == expected_hash, 'consumed normalization helper hash differs')
    block = epoch_neutral_normalizer(raw.decode('utf-8-sig'), layout)
    return {'fullFileSha256': hashlib.sha256(raw).hexdigest(), 'epochNeutralCalledBlockSha256': sha(block),
            'layoutVersion': layout, 'allowedEpochLiteralDifferences': 4}


def _normalized_diagnostic_source(text, side):
    need(side in ['baseline', 'current'], 'unsupported diagnostic source side')
    text = text.replace('\r\n', '\n')
    need('\r' not in text, side + ' diagnostic source has unsupported bare-CR line endings')
    for name, baseline_literal, current_literal in DIAGNOSTIC_ADAPTERS:
        expected = baseline_literal if side == 'baseline' else current_literal
        forbidden = current_literal if side == 'baseline' else baseline_literal
        need(text.count(expected) == DIAGNOSTIC_ADAPTER_OCCURRENCES and text.count(forbidden) == 0,
             side + ' diagnostic ' + name + ' literal count differs')
        text = text.replace(expected, '<' + name.upper().replace('-', '_') + '>')
    return text


def _compare_diagnostic_source_texts(baseline_text, current_text):
    baseline_normalized = _normalized_diagnostic_source(baseline_text, 'baseline')
    current_normalized = _normalized_diagnostic_source(current_text, 'current')
    need(baseline_normalized == current_normalized,
         'diagnostic source changed beyond the six declared epoch adapter literals')
    return baseline_normalized, current_normalized


def diagnostic_source_boundary(baseline_root, current_root, before_pinmap, after_pinmap):
    """Bind and compare the diagnostic emitter, allowing only its six epoch labels/guards."""
    source_path = DIAGNOSTIC_SOURCE_PATH
    need(type(before_pinmap) is dict and type(after_pinmap) is dict and
         source_path in before_pinmap and source_path in after_pinmap,
         'diagnostic source is absent from a native input pin map')
    source_limit = 2 * 1024 * 1024
    baseline_file = (Path(baseline_root).resolve(strict=True) / source_path).resolve(strict=True)
    current_file = (Path(current_root).resolve(strict=True) / source_path).resolve(strict=True)
    for root, path, side in [(Path(baseline_root).resolve(strict=True), baseline_file, 'baseline'),
                             (Path(current_root).resolve(strict=True), current_file, 'current')]:
        try:
            path.relative_to(root)
        except ValueError:
            raise ValueError(side + ' diagnostic source escapes its pinned snapshot root')
    baseline_raw, current_raw = baseline_file.read_bytes(), current_file.read_bytes()
    need(0 < len(baseline_raw) <= source_limit and 0 < len(current_raw) <= source_limit,
         'diagnostic source exceeds its bounded input size')
    baseline_hash, current_hash = hashlib.sha256(baseline_raw).hexdigest(), hashlib.sha256(current_raw).hexdigest()
    need(baseline_hash == before_pinmap[source_path] and current_hash == after_pinmap[source_path],
         'diagnostic source bytes differ from the frozen baseline/current source pins')
    baseline_text = baseline_raw.decode('utf-8')
    current_text = current_raw.decode('utf-8')
    baseline_normalized, current_normalized = _compare_diagnostic_source_texts(
        baseline_text, current_text)

    mutation = 'require(cases == 12, "selected saved-profile cohort emitted exactly twelve ordered cases");'
    need(current_text.count(mutation) == 1, 'diagnostic source mutation canary target differs')
    mutated = current_text.replace(mutation, mutation.replace('cases == 12', 'cases == 11'), 1)
    try:
        _compare_diagnostic_source_texts(baseline_text, mutated)
    except ValueError:
        mutation_canary = 'PASS_REJECTED'
    else:
        raise ValueError('diagnostic source logic mutation escaped the six-literal source boundary')

    return {
        'sourcePath': source_path,
        'baselineRawSha256': baseline_hash,
        'currentRawSha256': current_hash,
        'baselineBytes': len(baseline_raw),
        'currentBytes': len(current_raw),
        'baselineEpochNeutralSha256': sha(baseline_normalized),
        'currentEpochNeutralSha256': sha(current_normalized),
        'sameLogicAfterSixVersionLiteralAdapters': True,
        'allowedLiteralDifferences': [
            {'name': name, 'baselineLiteral': old, 'currentLiteral': new,
             'occurrences': DIAGNOSTIC_ADAPTER_OCCURRENCES}
            for name, old, new in DIAGNOSTIC_ADAPTERS],
        'sourceLogicMutationCanary': mutation_canary,
    }


def negative_canaries(observation, layout):
    rows = observation['cases']
    mutations = [lambda row: row.__setitem__('layoutQualification', True),
        lambda row: row.__setitem__('frameId', None),
        lambda row: row.__setitem__('p08SourceLandCount', float(row['p08SourceLandCount'])),
        lambda row: row.__setitem__('adopted', True), lambda row: row.__setitem__('rootSeed', -1017),
        lambda row: row.__setitem__('baselineExpected', '0' * 64),
        lambda row: row.__setitem__('layoutGeometryFingerprint', row['layoutGeometryFingerprint'] + ' '),
        lambda row: row.__setitem__('p08Canonical', row['p08Canonical'] + ' '),
        lambda row: row.__setitem__('p08SegmentLandPairs', row['p08SegmentLandPairs'] + 1),
        lambda row: row.__setitem__('traceGeometryFingerprint', row['traceGeometryFingerprint'][:-1]),
        lambda row: row['componentMappings'][0]['terminals'][0].__setitem__('declaredNetId', 'WRONG_NET')]
    checked = 0
    for mutate in mutations:
        row = copy.deepcopy(rows[0])
        mutate(row)
        try:
            validate_row(row, 0, layout)
        except ValueError:
            checked += 1
        else:
            raise ValueError('malformed geometry canary accepted')
    lines = [FRAME + json.dumps(observation['frame'])] + [CASE + json.dumps(row) for row in rows] + [
             SUMMARY + json.dumps(observation['summary']), observation['footer']]
    for invalid in [lines[:-1], [lines[-1]] + lines[:-1], lines + [lines[-2]], lines[:1] + lines[2:],
                    lines + ['FAIL: synthetic trailing failure']]:
        try:
            extract(invalid, layout)
        except ValueError:
            checked += 1
        else:
            raise ValueError('malformed native geometry framing accepted')
    for invalid in ['{"key":1,"key":2}', '{"key":NaN}']:
        try:
            strict_json(invalid)
        except ValueError:
            checked += 1
        else:
            raise ValueError('malformed geometry JSON accepted')
    return checked


def compare(baseline, current):
    comparisons = []
    for old, new in zip(baseline['cases'], current['cases'], strict=True):
        need(old['logicalInventoryCanonical'] == new['logicalInventoryCanonical'] and
             all(old[key] == new[key] for key in ['rootSeed', 'placementInterpretationVersion', 'placementSeed',
                 'routingSeed', 'topology', 'packageCensus', 'expectedPackages', 'expectedPads', 'declaredNets',
                 'physicalPolicyId', 'physicalPolicyVersion', 'placementDensityVersion', 'placementPackingVersion',
                 'traceWidthProfile']), 'unchanged authored board declaration/streams differ')
        logical_fields = ['componentId', 'type', 'kind', 'physicalPackage', 'value', 'channel', 'inputId', 'boardType']
        old_parts, new_parts = [{item['componentId']: item for item in row['componentMappings']} for row in [old, new]]
        need(old_parts.keys() == new_parts.keys(), 'component identities changed')
        changed = []
        for part_id, before in old_parts.items():
            after = new_parts[part_id]
            need(all(before[key] == after[key] for key in logical_fields), 'declared component changed: ' + part_id)
            need([(p['padId'], p['terminalId'], p['netId']) for p in before['terminals']] ==
                 [(p['padId'], p['terminalId'], p['netId']) for p in after['terminals']], 'terminal identities changed')
            if before != after:
                changed.append(part_id)
        comparisons.append({'seed': old['rootSeed'], 'placementInterpretation': old['placementInterpretationVersion'],
            'logicalDeclarationsAndStreamsUnchanged': True,
            'oldPhysicalSha256': old['normalizedPhysicalSha256'], 'currentPhysicalSha256': new['normalizedPhysicalSha256'],
            'selectedAttemptBefore': old['selectedPlacementAttempt'], 'selectedAttemptAfter': new['selectedPlacementAttempt'],
            'placementCanonicalUnchanged': old['placementCanonical'] == new['placementCanonical'],
            'changedComponentMappings': sorted(changed),
            'viasBefore': old['viaCount'], 'viasAfter': new['viaCount'],
            'segmentsBefore': old['traceSegmentCount'], 'segmentsAfter': new['traceSegmentCount'],
            'outlineBefore': old['boardOutline'], 'outlineAfter': new['boardOutline'],
            'policyBefore': old['policyStatisticsCanonical'], 'policyAfter': new['policyStatisticsCanonical'],
            'independentPhysicalAndPartitionWitnesses': 'PASS_NATIVE_BOTH_SOURCES'})
    return comparisons


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ['baseline', 'current', 'baseline-result', 'current-result', 'baseline-console', 'current-console',
                'baseline-pins', 'current-pins', 'consumed-current-pins', 'baseline-normalizer', 'current-normalizer', 'output']:
        parser.add_argument('--' + key, required=True, type=Path)
    args = parser.parse_args()
    need(not args.output.exists(), 'refusing an existing comparison output')
    baseline = read_observation(args.baseline, args.baseline_result, args.baseline_console, 22)
    current = read_observation(args.current, args.current_result, args.current_console, 24)
    baseline_pin_raw, current_pin_raw = args.baseline_pins.read_bytes(), args.current_pins.read_bytes()
    baseline_pin_sha, current_pin_sha = (hashlib.sha256(baseline_pin_raw).hexdigest(),
                                         hashlib.sha256(current_pin_raw).hexdigest())
    need(baseline_pin_sha == BASELINE_PIN_FILE_SHA256 and current_pin_sha == CURRENT_PIN_FILE_SHA256,
         'baseline/current source-pin snapshot bytes differ from the frozen native inputs')
    before, after = pins(args.baseline_pins), pins(args.current_pins)
    need(len(before) == len(after) == 1059,
         'baseline/current native snapshots must each contain 1059 source inputs')
    for side, observation, expected_revision, pin_sha in [
            ('baseline', baseline, 2, baseline_pin_sha), ('current', current, 3, current_pin_sha)]:
        result_metadata = observation['result']
        need(type(result_metadata) is dict and result_metadata.get('source') == side and
             integer(result_metadata.get('inputRevision'), expected_revision, expected_revision) and
             result_metadata.get('inputAudit') == 'PASS_BEFORE_AND_AFTER' and
             integer(result_metadata.get('inputCount'), 1059, 1059) and
             result_metadata.get('inputPinsSha256') == pin_sha,
             side + ' native invocation did not consume its exact sealed source-pin snapshot')
    baseline_only = sorted(set(before) - set(after))
    current_only = sorted(set(after) - set(before))
    need(baseline_only == REMOVED_TESTS and current_only == ADDED_TESTS,
         'baseline/current pin path delta differs from the exact retired/added contracts')
    common = set(before) & set(after)
    need(len(common) == 1055 and set(before) - set(REMOVED_TESTS) == set(after) - set(ADDED_TESTS),
         'baseline/current snapshots differ beyond the four retired and four added contracts')
    changes = sorted(key for key in common if before[key] != after[key])
    need(changes == COMMON_CHANGED,
         'native source inputs changed beyond the exact reviewed 15-path delta')
    production_changes = sorted(key for key in changes if key.startswith('src/'))
    need(production_changes == PRODUCTION_CHANGES,
         'production source changes differ from the four declared routing/epoch files')
    for key in UNCHANGED_ORACLE_INPUTS:
        need(key in before and key in after and before[key] == after[key],
             'comparison/oracle/tool input changed: ' + key)
    jars_before = {key: value for key, value in before.items() if key.endswith('.jar')}
    jars_after = {key: value for key, value in after.items() if key.endswith('.jar')}
    need(jars_before == jars_after, 'JDK/GWT jar input drifted across the baseline/current comparison')
    consumed = pins(args.consumed_current_pins)
    audited = [key for key in consumed if key in after]
    unmatched = sorted(key for key in consumed if key not in after)
    need(len(audited) >= 30 and all(consumed[key] == after[key] for key in audited),
         'post-run complete input audit differs from consumed frozen native inputs')
    need(not any(key.startswith('src/') or key.endswith('.jar') for key in unmatched),
         'consumed production/tool input is missing from the complete comparison set')
    for key in changes + ['tests/contracts/PcbCompactnessContractTest.java']:
        need(key in audited, 'missing consumed source proof: ' + key)
    helper_key = 'tests/contracts/PcbCompactnessContractTest.java'
    old_helper = normalizer_boundary(args.baseline_normalizer, before[helper_key], 22)
    new_helper = normalizer_boundary(args.current_normalizer, after[helper_key], 24)
    need(old_helper['epochNeutralCalledBlockSha256'] == new_helper['epochNeutralCalledBlockSha256'],
         'normalization helper changed beyond the four declared epoch literals')
    diagnostic_boundary = diagnostic_source_boundary(
        source_snapshot_root(args.baseline_normalizer, 'baseline'),
        source_snapshot_root(args.current_normalizer, 'current'), before, after)
    comparisons = compare(baseline, current)
    negatives = negative_canaries(baseline, 22) + negative_canaries(current, 24) + 1
    current_helper_text = args.current_normalizer.read_text(encoding='utf-8-sig')
    need(current_helper_text.count('return normalized;') == 1, 'normalizer mutation canary target differs')
    mutated = current_helper_text.replace('return normalized;', 'return fingerprint;')
    need(sha(epoch_neutral_normalizer(mutated, 24)) != old_helper['epochNeutralCalledBlockSha256'],
         'changed normalization logic escaped the consumed-helper comparison')
    negatives += 1
    try:
        epoch_neutral_normalizer(current_helper_text.replace('CURRENT_VERSION != 24', 'CURRENT_VERSION != 22'), 24)
    except ValueError:
        negatives += 1
    else:
        raise ValueError('wrong normalization epoch gate accepted')
    result = {'protocol': 'p07-six-board-epoch-transition-v2', 'status': 'PASS_COMPARISON_ONLY',
        'scope': SCOPE, 'layoutQualification': False, 'adopted': False, 'goldensUpdated': False,
        'baselineLayoutVersion': 22, 'baselineDependencyEpoch': 'tsj-generation-dependencies-v23',
        'currentLayoutVersion': 24, 'currentDependencyEpoch': 'tsj-generation-dependencies-v25',
        'baseline': baseline, 'current': current, 'productionChanges': production_changes,
        'baselineInputCount': len(before), 'currentInputCount': len(after), 'comparedInputCount': len(common),
        'baselinePinsSha256': baseline_pin_sha, 'currentPinsSha256': current_pin_sha,
        'baselineInputRevision': 2, 'currentInputRevision': 3,
        'comparisonInputScope': '1055_COMMON_INPUTS; FOUR_RETIRED_BASELINE_TESTS; FOUR_ADDED_CURRENT_TESTS',
        'baselineRetiredPaths': baseline_only, 'currentAddedPaths': current_only,
        'commonChangedPaths': changes,
        'comparisons': comparisons,
        'consumedCurrentPinsAudited': len(audited), 'negativeCanaries': negatives,
        'consumedCurrentNonCommonPaths': unmatched,
        'normalizationBoundary': {'baseline': old_helper, 'current': new_helper,
                                  'sameCalledLogicAfterFourExplicitEpochLiterals': True},
        'diagnosticSourceBoundary': diagnostic_boundary,
        'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    result['helperSha256'] = {'read_diagnostic.py': hashlib.sha256(
        Path(__file__).with_name('read_diagnostic.py').read_bytes()).hexdigest()}
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print('PASS: six old oracles reproduced; six layout24 layouts independently witnessed; comparison only, goldens unchanged')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, KeyError, TypeError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
