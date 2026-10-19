"""Independent reader for the native-only source-sized passive-pose trial."""
import hashlib
import json
import math
import re
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOTS = ['-1017', '-1004', '-1022', '0', '17', '42', '1', '7', '5', '14', '2', '3', '4', '6']
METRICS = ('area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias')
PASSIVES = {'AXIAL_RESISTOR', 'AXIAL_DIODE', 'RADIAL_ELECTROLYTIC_CAPACITOR', 'RADIAL_CERAMIC_CAPACITOR'}
BASE_SHA = '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
RAW_SHA = 'c896f2ee0cb553220f5e7a4814e6ae8b7aecc21a2f7c787803db42e8a76f1790'
SOURCE_FIXTURE_SHA = 'e58c5e72aea79e65efcb427a643310545742a5161d83072b54fccdb0397f9570'
UNIT_SUITES_SHA = '8521298e51c302b65ab7ceaa66d6d405c5ee33722d2a2b2a7ed028a1d90b000b'
ROUTES_SUITES_SHA = '0f48566d9b26530e7919e6230eb9c0dec081d7d33848db47073fc1638ee0e0a0'
TIMING_SCOPE = 'Native source-sized orthogonal trial; unchanged per-JVM/search/physical limits'
WHITESPACE_SCOPE = ('GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_'
                    'ISOLATION;FREE_IS_NOT_PACKABLE_PROOF')
INPUT16_PREFIX = 'PCB_PASSIVE_POLICY_INPUT '
SOURCE18_PREFIX = 'PCB_SOURCE_SIZED_POSE_INPUT '
SOURCE15_PREFIX = 'PCB_PASSIVE_POSE_SOURCE_CASE '
RAW_PREFIX = 'PCB_SOURCE_SIZED_POSE_RAW '
WHITESPACE_PREFIX = 'PCB_WHITESPACE_CASE '
POSE_PREFIX = 'PCB_SOURCE_SIZED_POSE_POSES '
ROUTE_PREFIX = 'PCB_SOURCE_SIZED_POSE_ROUTE '
REPORT_PREFIXES = (SOURCE18_PREFIX, RAW_PREFIX, SOURCE15_PREFIX, INPUT16_PREFIX,
                   WHITESPACE_PREFIX, POSE_PREFIX, ROUTE_PREFIX)
SOURCE16_FIELDS = ('seed', 'sourceCanonical15', 'sourcePlanSha25616', 'graph16',
                   'intent16', 'layoutSeed16', 'routingSeed16', 'eligibleIds')
SOURCE15_FIELDS = ('seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
                   'sourceIntentSha256', 'layoutSeed', 'routingSeed')
SOURCE18_FIELDS = ('seed', 'sourceCanonical15', 'sourcePlanSha25618', 'graph18', 'intent18',
                   'layoutSeed18', 'routingSeed18', 'eligibleIds', 'densityVersion18',
                   'passivePosePolicy18', 'admissionIdentity18')
RAW_FIELDS = ('seed', 'attempt', 'geometry15', 'geometry18', 'evaluations15', 'evaluations18',
              'demand15', 'demand18', 'courtyard15', 'courtyard18')
RAW_SAVED_FIELDS = ('seed', 'attempt', 'geometry15', 'geometry16', 'evaluations15',
                    'evaluations16', 'demand15', 'demand16', 'courtyard15', 'courtyard16')
POSE_FIELDS = ('seed', 'issuedAdmissionIdentity', 'version', 'top0', 'top90')
WHITESPACE_FIELDS = ('seed', 'version', 'parts', 'gridUnits', 'sampledCells',
                     'packageAccessOccupiedCells', 'protectedFreeCells',
                     'protectedFreeFraction', 'largestProtectedEmptyRectangle', 'scope')
SAVED_FIELDS = (
    'status', 'seed', 'version', 'density', 'parts', 'pads', 'area', 'width', 'height',
    'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'geometrySha256',
    'sourcePlanSha256', 'liveBoardGraphSha256', 'sourceIntentSha256', 'layoutSeed',
    'routingSeed', 'selectedAttempt', 'rankedPlacementAttempts', 'acceptedPlanEvaluations',
    'placementRejections', 'oneFaceAttempts', 'twoLayerAttempts', 'routeAttempts',
    'routingOrders', 'routingExpansions', 'selectedQualityMilli', 'statisticsCanonical',
    'fixedMarginFloor', 'accessEnvelopeMargin', 'requiredMargin', 'legacyRequestedMargin',
    'requestedMargin', 'actualMargin', 'occupiedX', 'occupiedY', 'occupiedWidth',
    'occupiedHeight', 'outlineX', 'outlineY', 'excludedWorkbenchChrome', 'elapsedMillis')
FAILURE_LINE = re.compile(
    r'^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|AssertionError\b|'
    r'BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|TIMEOUT\b|TIMED OUT\b|'
    r'CURRENT_CONTRACT_TIMEOUT:|CANCELLED\b)', re.IGNORECASE)
LONG_TEXT = re.compile(r'-?(?:0|[1-9][0-9]*)\Z')
HEX64 = re.compile(r'[0-9a-f]{64}\Z')


def _need(condition, message):
    if not condition:
        raise AssertionError(message)


def digest(value):
    _need(type(value) is str, 'digest input must be text')
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def long_text(value):
    _need(type(value) is str and LONG_TEXT.fullmatch(value) is not None,
          'seed must be canonical signed-long text')
    number = int(value)
    _need(-(1 << 63) <= number < (1 << 63) and str(number) == value,
          'seed is outside exact signed-long range')
    return value


def _read_json(path, expected_sha=None):
    data = path.read_bytes()
    if expected_sha is not None:
        _need(hashlib.sha256(data).hexdigest() == expected_sha, 'sealed input changed: ' + path.name)
    return json.loads(data.decode('utf-8'))


def _baselines():
    doc = _read_json(HERE / 'accepted15-baselines.json', BASE_SHA)
    _need(type(doc) is dict and type(doc.get('rows')) is list, 'accepted15 baseline shape changed')
    rows = doc['rows']
    _need(len(rows) == 14 and [row.get('seed') for row in rows] == ROOTS,
          'accepted15 roots/order changed')
    for row in rows:
        _need(type(row) is dict and tuple(row) == SAVED_FIELDS, 'accepted15 schema/order changed')
        _need(row['status'] == 'PASS' and row['version'] == 15 and row['density'] == 14,
              'accepted15 identity changed')
        _need(type(row['elapsedMillis']) is int and 0 <= row['elapsedMillis'] <= 60000,
              'accepted15 timing outside declared cap')
    return rows


def _raw_baselines():
    doc = _read_json(HERE / 'raw-baselines.json', RAW_SHA)
    _need(type(doc) is dict and type(doc.get('rows')) is list and len(doc['rows']) == 15,
          'sealed raw baseline shape changed')
    _need(doc.get('sourceSha256') == SOURCE_FIXTURE_SHA, 'sealed raw source binding changed')
    for row in doc['rows']:
        _need(type(row) is dict and tuple(row) == RAW_SAVED_FIELDS,
              'sealed raw baseline row schema/order changed')
        _need(row['geometry15'] == row['geometry16'], 'sealed raw geometry15/16 differ')
        for name in ('evaluations', 'demand', 'courtyard'):
            _need(type(row[name + '15']) is int and row[name + '15'] == row[name + '16'],
                  'sealed raw15/16 ' + name + ' values differ')
    return doc['rows']


def _rows(text, prefix):
    _need(type(text) is str, 'native report text required')
    rows = []
    for line in text.splitlines():
        if line.startswith(prefix):
            row = json.loads(line[len(prefix):])
            _need(type(row) is dict, 'report row must be a JSON object: ' + prefix.strip())
            rows.append(row)
    return rows


def _all_report_rows(text):
    _need(type(text) is str, 'native phase text required')
    rows = []
    for line in text.splitlines():
        if line.startswith('PCB_'):
            prefix = next((item for item in REPORT_PREFIXES if line.startswith(item)), None)
            _need(prefix is not None, 'unknown native report line')
            row = json.loads(line[len(prefix):])
            _need(type(row) is dict, 'report row must be a JSON object')
            rows.append((prefix, row, line))
    return rows


def _ordered_cases(rows):
    return [(seed, version) for seed in ROOTS for version in (15, 18)]


def _suite_pairs(suites):
    _need(type(suites) is list and bool(suites), 'frozen suite list required')
    pairs = []
    for item in suites:
        _need(type(item) in (list, tuple) and len(item) == 2, 'suite pair shape changed')
        name, marker = item
        _need(type(name) is str and re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', name),
              'suite class name malformed')
        _need(type(marker) is str and marker and '\n' not in marker and '\r' not in marker,
              'suite marker malformed')
        pairs.append((name, marker))
    _need(len({name for name, _ in pairs}) == len(pairs), 'duplicate suite class')
    _need(len({marker for _, marker in pairs}) == len(pairs), 'duplicate suite marker')
    return pairs


def _marker_detail(line, marker):
    suffix = line[len('PASS: ' + marker):].strip()
    if not suffix:
        _need('admission contracts' in marker or 'source bindings' in marker,
              'required suite marker details are absent')
        return
    allowed = {'roots', 'assertions', 'rows', 'routes', 'geometries', 'sequence', 'visits', 'cases'}
    if 'policy contracts' in marker or 'admission contracts' in marker:
        allowed = {'assertions', 'geometries', 'sequence', 'visits'}
    elif 'raw' in marker:
        allowed = {'rows', 'routes', 'assertions', 'cases'}
    elif 'source contracts' in marker:
        allowed = {'roots', 'assertions'}
    elif 'route' in marker:
        allowed = {'roots', 'rows', 'routes', 'assertions', 'geometries', 'sequence', 'visits'}
    token = re.compile(r'([A-Za-z][A-Za-z0-9_-]*)=([0-9]+(?:/[0-9]+)?)')
    at = 0
    found = 0
    values = {}
    if 'admission contracts' in marker and re.fullmatch(r'[0-9]+\s+assertions', suffix):
        _need(int(suffix.split()[0]) <= 1000000, 'admission assertion count exceeds bound')
        return
    for match in token.finditer(suffix):
        _need(re.fullmatch(r'[\s;,]*', suffix[at:match.start()]) is not None,
              'suite marker has unbounded detail')
        key, value = match.groups()
        _need(key in allowed, 'suite marker has an unknown detail key')
        numbers = [int(part) for part in value.split('/')]
        _need(all(number <= 1000000 for number in numbers), 'suite marker detail exceeds bound')
        _need(len(numbers) == 1 or numbers[1] > 0, 'suite marker ratio is malformed')
        _need(key not in values, 'suite marker detail key duplicated')
        values[key] = value
        at = match.end()
        found += 1
    _need(found > 0 and re.fullmatch(r'[\s;,]*', suffix[at:]) is not None,
          'suite marker detail is malformed')
    if 'policy contracts' in marker:
        required = {'geometries': '7', 'sequence': '63/63', 'visits': '126'}
    elif 'raw' in marker:
        required = {'rows': '5', 'routes': '0'}
    elif 'source contracts' in marker:
        required = {'roots': '14'}
    elif 'route' in marker:
        required = {'roots': '1', 'rows': '2'}
    else:
        required = {}
    _need(all(values.get(key) == value for key, value in required.items()),
          'suite marker coverage/count details changed')


def _expected_report_prefixes(phase):
    if phase == 'unit':
        return ([SOURCE18_PREFIX] * 14 + [RAW_PREFIX] * 15 + [SOURCE15_PREFIX] * 14 +
                [INPUT16_PREFIX] * 14)
    return [prefix for _ in _ordered_cases(None)
            for prefix in (WHITESPACE_PREFIX, POSE_PREFIX, ROUTE_PREFIX)]


def _check_report_identity(phase, rows):
    prefixes = [prefix for prefix, _, _ in rows]
    _need(prefixes == _expected_report_prefixes(phase), 'native report missing, extra, or reordered')
    if phase == 'unit':
        groups = ((SOURCE18_PREFIX, ROOTS), (SOURCE15_PREFIX, ROOTS), (INPUT16_PREFIX, ROOTS))
        for prefix, roots in groups:
            actual = [row.get('seed') for p, row, _ in rows if p == prefix]
            _need(actual == roots, 'unit report roots missing, duplicate, or reordered')
        raw_rows = [row for p, row, _ in rows if p == RAW_PREFIX]
        raw_cases = [(seed, attempt) for seed in ('42', '-1022', '4')
                     for attempt in (0, 2, 3, 4, 5)]
        _need([(row.get('seed'), row.get('attempt')) for row in raw_rows] == raw_cases,
              'raw report cases missing, duplicate, or reordered')
    else:
        expected = _ordered_cases(None)
        actual = []
        for prefix, row, _ in rows:
            _need(prefix in (WHITESPACE_PREFIX, POSE_PREFIX, ROUTE_PREFIX),
                  'unexpected route phase report')
            actual.append((row.get('seed'), row.get('version')))
        _need(actual == [case for case in expected for _ in range(3)],
              'route whitespace/pose/route cases missing, extra, or reordered')
        for index in range(0, len(rows), 3):
            _need([rows[index + offset][0] for offset in range(3)] ==
                  [WHITESPACE_PREFIX, POSE_PREFIX, ROUTE_PREFIX],
                  'each route case requires whitespace before pose and route')


def check_phase(text, receipt, timing, suites):
    """Validate one maintained native phase log, receipt, timing, reports, and suite order."""
    _need(type(text) is str and type(receipt) is str and type(timing) is dict,
          'phase text, receipt, and timing required')
    phase = timing.get('phase')
    _need(phase in ('unit', 'routes'), 'unknown native phase')
    manifest_path = HERE / ('unit-suites.json' if phase == 'unit' else 'routes-suites.json')
    expected_sha = UNIT_SUITES_SHA if phase == 'unit' else ROUTES_SUITES_SHA
    manifest = _read_json(manifest_path, expected_sha)
    pairs = _suite_pairs(suites)
    _need(pairs == _suite_pairs(manifest), 'caller suite order differs from frozen manifest')
    suite_count = len(pairs)
    _need(suite_count == (8 if phase == 'unit' else 14), 'frozen suite count changed')
    _need(set(timing) == {'status', 'exitCode', 'elapsedSeconds', 'suiteCount', 'phase', 'scope',
                          'terminalCleanupIncluded', 'cleanupDuration'}, 'native timing schema changed')
    elapsed = timing['elapsedSeconds']
    _need(timing['status'] == 'PASS' and type(timing['exitCode']) is int and timing['exitCode'] == 0,
          'native phase status/exit failed')
    _need(type(timing['suiteCount']) is int and timing['suiteCount'] == suite_count,
          'native suite count changed')
    _need(type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0,
          'native elapsed time malformed')
    _need(timing['terminalCleanupIncluded'] is True and
          timing['cleanupDuration'] == 'NOT_SEPARATELY_MEASURED' and timing['scope'] == TIMING_SCOPE,
          'native timing scope or cleanup boundary changed')

    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) for line in text_lines + receipt_lines),
          'native phase contains failure, timeout, or cancellation output')
    terminal = ('PASS: focused current contracts; %d Java suites. Full matrix and independent oracles NOT RUN.'
                % suite_count)
    cleanup = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
    _need(text_lines.count(terminal) == 1 and text_lines.count(cleanup) == 1,
          'native terminal PASS or cleanup marker missing/duplicated')
    _need(terminal not in receipt_lines and cleanup not in receipt_lines,
          'child receipt contains outer terminal data')
    _need(text_lines.index(terminal) < text_lines.index(cleanup), 'cleanup precedes terminal result')

    suite_passes = [line for line in text_lines if line.startswith('PASS: ') and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith('PASS: ')]
    _need(suite_passes == receipt_passes and len(suite_passes) == suite_count,
          'full suite PASS lines differ between receipt and terminal log')
    for line, (_, marker) in zip(suite_passes, pairs):
        _need(line.startswith('PASS: ' + marker), 'suite PASS marker missing, duplicate, or reordered')
        _marker_detail(line, marker)
    _need(all(text_lines.index(line) < text_lines.index(terminal) for line in suite_passes),
          'suite PASS appeared after terminal marker')

    text_reports, receipt_reports = _all_report_rows(text), _all_report_rows(receipt)
    _check_report_identity(phase, text_reports)
    _need([line for _, _, line in text_reports] == [line for _, _, line in receipt_reports],
          'terminal and child report rows differ')
    _need(all(text_lines.index(line) < text_lines.index(terminal) for _, _, line in text_reports),
          'native report appeared after terminal marker')
    return {'status': 'PASS', 'phase': phase, 'suiteCount': suite_count,
            'suiteClasses': [name for name, _ in pairs],
            'reportRows': len(text_reports), 'orderedMarkers': [marker for _, marker in pairs]}


def _canonical_identity(canonical, old):
    _need(type(canonical) is str and 0 < len(canonical) <= 256 * 1024,
          'source canonical size/type changed')
    _need(digest(canonical) == old['sourcePlanSha256'], 'canonical source no longer matches sealed15 plan')
    marker = 'placement-interpretation=15;'
    _need(canonical.count(marker) == 1, 'source interpretation marker must occur exactly once')
    sha16 = digest(canonical.replace(marker, 'placement-interpretation=16;', 1))
    sha18 = digest(canonical.replace(marker, 'placement-interpretation=18;', 1))
    parts = re.findall(r'\[([^\[\]]+)\]', canonical)
    _need(len(parts) == old['parts'] and
          sum(len(part.split('|')) - 1 for part in parts) == old['pads'],
          'canonical source population/pad count changed')
    headers = [part.split('|')[0].split(':') for part in parts]
    _need(all(len(header) == 7 for header in headers), 'canonical component schema changed')
    identities = [header[0] for header in headers]
    _need(len(set(identities)) == len(identities), 'duplicate source component identity')
    eligible = sorted(header[0] for header in headers if header[3] in PASSIVES)
    _need(eligible == sorted(set(eligible)), 'passive eligibility census duplicated')
    return sha16, sha18, eligible


def _ordered_source_records(unit_text, prefix, expected_count=14):
    rows = _rows(unit_text, prefix)
    _need(len(rows) == expected_count, 'source report count changed: ' + prefix.strip())
    _need([row.get('seed') for row in rows] == ROOTS, 'source report roots missing, duplicate, or reordered')
    return rows


def check_sources(unit_text):
    """Return 14 source bindings after verifying v15, unchanged v16, and derived v18 identities."""
    old_rows = _baselines()
    rows18 = _ordered_source_records(unit_text, SOURCE18_PREFIX)
    rows15 = _ordered_source_records(unit_text, SOURCE15_PREFIX)
    rows16 = _ordered_source_records(unit_text, INPUT16_PREFIX)
    bindings = {}
    for old, row15, row16, row18 in zip(old_rows, rows15, rows16, rows18):
        _need(type(row15) is dict and tuple(row15) == SOURCE15_FIELDS,
              'old source binding schema/order changed')
        _need(long_text(row15['seed']) == old['seed'], 'old source signed-long identity changed')
        _need(type(row15['parts']) is int and row15['parts'] == old['parts'] and
              type(row15['pads']) is int and row15['pads'] == old['pads'],
              'old source population changed')
        for key in ('sourcePlanSha256', 'liveBoardGraphSha256', 'sourceIntentSha256'):
            _need(row15[key] == old[key], 'old source sealed identity changed: ' + key)
        _need(long_text(row15['layoutSeed']) == old['layoutSeed'] and
              long_text(row15['routingSeed']) == old['routingSeed'],
              'old source signed-long layout/routing seed changed')
        _need(type(row16) is dict and tuple(row16) == SOURCE16_FIELDS,
              'trial16 source binding schema/order changed')
        _need(type(row18) is dict and tuple(row18) == SOURCE18_FIELDS,
              'trial18 source binding schema/order changed')
        canonical = row18['sourceCanonical15']
        sha16, sha18, eligible = _canonical_identity(canonical, old)
        _need(row16['sourceCanonical15'] == canonical and row16['sourcePlanSha25616'] == sha16,
              'trial16 canonical/hash changed from derived source')
        for version, record in ((16, row16), (18, row18)):
            graph = record['graph16'] if version == 16 else record['graph18']
            intent = record['intent16'] if version == 16 else record['intent18']
            layout = record['layoutSeed16'] if version == 16 else record['layoutSeed18']
            routing = record['routingSeed16'] if version == 16 else record['routingSeed18']
            ids = record['eligibleIds']
            _need(graph == old['liveBoardGraphSha256'] and intent == old['sourceIntentSha256'],
                  'source graph or intent identity changed')
            _need(long_text(layout) == old['layoutSeed'] and long_text(routing) == old['routingSeed'],
                  'source layout/routing signed-long seed changed')
            _need(type(ids) is list and all(type(value) is str for value in ids) and ids == eligible,
                  'source eligible passive population changed')
        _need(row18['sourcePlanSha25618'] == sha18, 'trial18 digest is not independently derived')
        _need(type(row18['densityVersion18']) is int and row18['densityVersion18'] == 14,
              'trial18 density identity changed')
        _need(type(row18['passivePosePolicy18']) is int and row18['passivePosePolicy18'] == 3,
              'trial18 passive pose policy changed')
        _need(row18['admissionIdentity18'] == 'RB56_BOARD_NORMAL@4',
              'trial18 admission identity changed')
        bindings[old['seed']] = {
            'seed': old['seed'], 'sourcePlanSha25616': sha16, 'sourcePlanSha25618': sha18,
            'graph': old['liveBoardGraphSha256'], 'intent': old['sourceIntentSha256'],
            'layoutSeed': old['layoutSeed'], 'routingSeed': old['routingSeed'],
            'parts': old['parts'], 'pads': old['pads'], 'eligibleIds': eligible,
            'density': 14, 'passivePosePolicy': 3, 'admissionIdentity': 'RB56_BOARD_NORMAL@4'}
    _need(set(bindings) == set(ROOTS), 'source bindings do not cover all14 roots')
    return bindings


def check_raw(unit_text, bindings):
    """Return 15 ordered raw candidate comparisons, preserving sealed15 behavior."""
    _need(type(bindings) is dict and set(bindings) == set(ROOTS), '14 verified source bindings required')
    rows = _rows(unit_text, RAW_PREFIX)
    baseline = _raw_baselines()
    expected = [(seed, attempt) for seed in ('42', '-1022', '4') for attempt in (0, 2, 3, 4, 5)]
    _need(len(rows) == 15 and [(row.get('seed'), row.get('attempt')) for row in rows] == expected,
          'all15 ordered raw cases required')
    _need([(row['seed'], row['attempt']) for row in baseline] == expected,
          'sealed raw baseline order changed')
    for row, old in zip(rows, baseline):
        _need(type(row) is dict and tuple(row) == RAW_FIELDS, 'raw row schema/order changed')
        _need(type(row['seed']) is str and type(row['attempt']) is int, 'raw identity type changed')
        _need(row['seed'] in bindings, 'raw seed has no verified source binding')
        _need(row['geometry15'] == old['geometry15'] and HEX64.fullmatch(row['geometry15']) is not None,
              'raw15 geometry changed')
        _need(HEX64.fullmatch(row['geometry18']) is not None and
              row['geometry18'] == row['geometry15'] == old['geometry15'],
              'raw18 geometry differs from untouched raw15 baseline')
        for name in ('evaluations', 'demand', 'courtyard'):
            _need(type(row[name + '15']) is int and row[name + '15'] == old[name + '15'],
                  'raw15 ' + name + ' changed')
            _need(type(row[name + '18']) is int and
                  row[name + '18'] == row[name + '15'] == old[name + '15'],
                  'raw18 ' + name + ' differs from untouched raw15 baseline')
        _need(0 < row['evaluations15'] <= 60000 and 0 < row['evaluations18'] <= 60000,
              'raw placement evaluation cap/type changed')
        _need(row['demand15'] > 0 and row['demand18'] > 0 and
              row['courtyard15'] > 0 and row['courtyard18'] > 0,
              'raw demand/courtyard must be positive')
    return rows


def _route_case(row, old, version, binding):
    _need(type(row) is dict and set(row) == set(old) and set(old) == set(SAVED_FIELDS),
          'routed row schema changed')
    for key in old:
        _need(type(row[key]) is type(old[key]), 'routed field type changed: ' + key)
    _need(type(row['elapsedMillis']) is int and 0 <= row['elapsedMillis'] <= 60000,
          'routed elapsed time outside unchanged per-JVM cap')
    if version == 15:
        _need(all(row[key] == value for key, value in old.items() if key != 'elapsedMillis'),
              'saved15 non-time42 fields changed: ' + old['seed'])
        return
    _need(version == 18 and row['status'] == 'PASS' and row['density'] == 14,
          'trial18 route did not pass at density14')
    for key in ('seed', 'parts', 'pads', 'liveBoardGraphSha256', 'sourceIntentSha256',
                'layoutSeed', 'routingSeed', 'excludedWorkbenchChrome'):
        _need(row[key] == old[key], 'trial18 source/population drift: ' + key)
    _need(row['sourcePlanSha256'] == binding['sourcePlanSha25618'] and
          row['liveBoardGraphSha256'] == binding['graph'] and
          row['sourceIntentSha256'] == binding['intent'], 'trial18 source binding mismatch')
    _need(HEX64.fullmatch(row['geometrySha256']) is not None, 'trial18 geometry digest malformed')
    _need(row['width'] > 0 and row['height'] > 0 and row['area'] == row['width'] * row['height'],
          'trial18 outline dimensions/area inconsistent')
    _need(row['actualMargin'] >= row['requiredMargin'] >= 0 and
          row['requestedMargin'] == row['legacyRequestedMargin'], 'trial18 margin contract changed')
    _need(0 < row['acceptedPlanEvaluations'] <= 6 * 60000 and
          0 <= row['placementRejections'] <= 6, 'trial18 placement cap changed')
    _need(0 <= row['oneFaceAttempts'] <= 3 and 1 <= row['twoLayerAttempts'] <= 3 and
          row['routeAttempts'] == row['oneFaceAttempts'] + row['twoLayerAttempts'] <= 6,
          'trial18 route attempt caps changed')
    ranked = row['rankedPlacementAttempts']
    _need(type(ranked) is list and 1 <= len(ranked) <= 6 and len(set(ranked)) == len(ranked) and
          all(type(value) is int and 0 <= value < 6 for value in ranked) and
          row['selectedAttempt'] in ranked, 'trial18 ranked placement identity changed')
    _need(row['selectedQualityMilli'] >= 0 and row['routingOrders'] >= 0 and
          row['routingExpansions'] >= 0, 'trial18 routing counters malformed')
    _need(type(row['statisticsCanonical']) is str and ';outcome=SUCCESS;' in row['statisticsCanonical'] and
          ';selected=P07_FULLER_TWO_LAYER;' in row['statisticsCanonical'] and
          ';placements=6;' in row['statisticsCanonical'], 'trial18 route success summary changed')
    _need(all(type(row[key]) is int and row[key] >= 0 for key in METRICS),
          'trial18 fitness metric type/range changed')


def _pose_case(row, old, version, binding):
    _need(type(row) is dict and tuple(row) == POSE_FIELDS, 'pose census schema/order changed')
    _need(row['seed'] == old['seed'] and type(row['version']) is int and row['version'] == version,
          'pose case identity changed')
    identity = 'RB56_BOARD_NORMAL@1' if version == 15 else 'RB56_BOARD_NORMAL@4'
    _need(row['issuedAdmissionIdentity'] == identity, 'pose admission identity changed')
    for key in ('top0', 'top90'):
        _need(type(row[key]) is list and all(type(value) is str for value in row[key]) and
              row[key] == sorted(set(row[key])), 'pose census must be ordered and unique')
    _need(not set(row['top0']) & set(row['top90']) and
          sorted(row['top0'] + row['top90']) == binding['eligibleIds'],
          'pose census lost, added, or duplicated a passive')
    if version == 15:
        _need(row['top90'] == [], 'saved15 pose census must remain TOP0-only')


def _rectangle_area(value, grid, route):
    _need(type(value) is dict and set(value) == {'x', 'y', 'width', 'height', 'area'},
          'protected rectangle must use the exact native five-field object')
    x, y, width, height, reported = (value[key] for key in ('x', 'y', 'width', 'height', 'area'))
    _need(all(type(item) is int for item in (x, y, width, height, reported)) and
          width > 0 and height > 0 and width % grid == 0 and height % grid == 0,
          'protected rectangle coordinate/dimension type or grid changed')
    _need(type(route.get('outlineX')) is int and type(route.get('outlineY')) is int and
          (x - route['outlineX']) % grid == 0 and (y - route['outlineY']) % grid == 0,
          'protected rectangle is not aligned to the route outline origin')
    area = width * height
    _need(reported == area, 'protected rectangle area disagrees with its dimensions')
    return area


def _whitespace_case(row, route, version, old):
    _need(type(row) is dict and tuple(row) == WHITESPACE_FIELDS,
          'whitespace row schema/order changed')
    _need(row['seed'] == old['seed'] and type(row['version']) is int and row['version'] == version,
          'whitespace case identity changed')
    _need(type(row['parts']) is int and row['parts'] == route['parts'],
          'whitespace part population differs from full route')
    _need(type(row['gridUnits']) is int and row['gridUnits'] == 10,
          'whitespace grid must remain10')
    counts = ('sampledCells', 'packageAccessOccupiedCells', 'protectedFreeCells')
    _need(all(type(row[key]) is int for key in counts), 'whitespace cell count type changed')
    sampled, occupied, free = (row[key] for key in counts)
    _need(sampled > 0 and 0 <= occupied <= sampled and 0 <= free <= sampled,
          'whitespace occupancy count outside sampled grid')
    fraction = row['protectedFreeFraction']
    _need(type(fraction) in (int, float) and math.isfinite(fraction) and 0 <= fraction <= 1 and
          abs(fraction - free / sampled) <= 1e-9, 'whitespace free-cell fraction inconsistent')
    area = _rectangle_area(row['largestProtectedEmptyRectangle'], row['gridUnits'], route)
    _need(area == route['largestProtectedEmptyRectangleArea'],
          'whitespace protected rectangle area differs from full-route metric')
    _need(row['scope'] == WHITESPACE_SCOPE, 'whitespace declared scope changed')


def assess_routes(route_rows, pose_rows, whitespace_rows, bindings):
    """Assess exact14 old/new pairs; return fidelity and strict-fitness results separately."""
    old_rows = _baselines()
    _need(type(bindings) is dict and set(bindings) == set(ROOTS), '14 verified source bindings required')
    expected = _ordered_cases(None)
    for name, rows in (('route', route_rows), ('pose', pose_rows), ('whitespace', whitespace_rows)):
        _need(type(rows) is list and len(rows) == 28, 'all28 ordered ' + name + ' rows required')
        _need([(row.get('seed'), row.get('version')) for row in rows] == expected,
              'all28 ' + name + ' cases must retain root/15-then-18 order')
    regressions, improvements, turned, deltas = [], [], [], []
    for index, (seed, version) in enumerate(expected):
        old = old_rows[index // 2]
        binding = bindings[seed]
        route, pose, whitespace = route_rows[index], pose_rows[index], whitespace_rows[index]
        _route_case(route, old, version, binding)
        _pose_case(pose, old, version, binding)
        _whitespace_case(whitespace, route, version, old)
        if version == 18:
            delta = {key: route[key] - old[key] for key in METRICS}
            deltas.append({'seed': seed, 'delta': delta})
            increased = [key for key, value in delta.items() if value > 0]
            improved = [key for key, value in delta.items() if value < 0]
            if increased:
                regressions.append({'seed': seed, 'metrics': increased})
            if improved:
                improvements.append({'seed': seed, 'metrics': improved})
            if pose['top90']:
                turned.append(seed)
    fidelity = {'status': 'PASS', 'saved15ExactCases': 14, 'saved15NonTimeFieldsExact': 42,
                'trial18StructuredCases': 14, 'whitespaceCases': 28,
                'poseCases': 28, 'limits': 'Maintained route/pass markers establish physical and access core checks'}
    no_regressions = not regressions
    has_improvement = bool(improvements)
    has_actual_turn = bool(turned)
    strict_ok = no_regressions and has_improvement and has_actual_turn
    strict = {'status': 'PASS' if strict_ok else 'FAIL',
              'noMetricRegression': no_regressions, 'strictImprovement': has_improvement,
              'actualCanonicalTop90': has_actual_turn, 'metricRegressions': regressions,
              'strictImprovements': improvements, 'selectedTop90Cases': turned,
              'metricDeltas': deltas,
              'failureReasons': [reason for condition, reason in (
                  (no_regressions, 'one or more trial18 metrics regress against paired15'),
                  (has_improvement, 'no strict trial18 metric improvement across all14 roots'),
                  (has_actual_turn, 'no actual canonical TOP90 passive selected across all14 roots'))
                  if not condition]}
    return {'status': 'PASS' if strict_ok else 'FAIL', 'fidelity': fidelity,
            'strictFitness': strict, 'policyPromotion': False,
            'limits': 'Finite native-only diagnostic; no replay, repair, manufacturing, GWT, player, or production-promotion proof'}
