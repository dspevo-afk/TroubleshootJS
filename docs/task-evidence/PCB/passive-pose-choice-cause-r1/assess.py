"""Strict read-only reader for the bounded passive-pose choice diagnosis."""
import hashlib
import importlib.util
import json
import math
import re
import struct
from decimal import Decimal, InvalidOperation
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOTS = ('42', '3')
CASES = (('42', 15), ('42', 16), ('3', 15), ('3', 16))
ROUTE_BASELINES_SHA256 = 'aa17f3137be2b929d2bcaf3651640b34a7db511010b5d4a05c1f813799013b19'
PRIOR_ASSESS_SHA256 = 'c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26'
SOURCE_FIELDS = ('seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
                 'sourceIntentSha256', 'layoutSeed', 'routingSeed')
ROUTE_FIELDS = (
    'status', 'seed', 'version', 'density', 'parts', 'pads', 'area', 'width', 'height',
    'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias', 'geometrySha256',
    'sourcePlanSha256', 'liveBoardGraphSha256', 'sourceIntentSha256', 'layoutSeed',
    'routingSeed', 'selectedAttempt', 'rankedPlacementAttempts', 'acceptedPlanEvaluations',
    'placementRejections', 'oneFaceAttempts', 'twoLayerAttempts', 'routeAttempts',
    'routingOrders', 'routingExpansions', 'selectedQualityMilli', 'statisticsCanonical',
    'fixedMarginFloor', 'accessEnvelopeMargin', 'requiredMargin', 'legacyRequestedMargin',
    'requestedMargin', 'actualMargin', 'occupiedX', 'occupiedY', 'occupiedWidth',
    'occupiedHeight', 'outlineX', 'outlineY', 'excludedWorkbenchChrome', 'elapsedMillis')
TIMING_FIELDS = {'status', 'exitCode', 'elapsedSeconds', 'suiteCount', 'phase', 'scope',
                 'terminalCleanupIncluded', 'cleanupDuration'}
TERMINAL = 'PASS: focused current contracts; {count} Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
TIMING_SCOPE = 'Read-only candidate1 choice trace; unchanged native per-JVM/search limits'
SOURCE_PREFIX = 'PCB_PASSIVE_POSE_SOURCE_CASE '
POLICY_INPUT_PREFIX = 'PCB_PASSIVE_POLICY_INPUT '
ROUTE_PREFIX = 'PCB_PASSIVE_CHOICE_ROUTE '
CAPTURE_PREFIX = 'PCB_PASSIVE_CHOICE_CAPTURE '
PROTOCOL_SAMPLE_PREFIX = 'PCB_PASSIVE_CHOICE_PROTOCOL_SAMPLE '
RAW_PREFIXES = ('PCB_PASSIVE_POLICY_RAW ', 'PCB_PASSIVE_POLICY_ROUTE ',
                 'PCB_PASSIVE_POLICY_POSES ')
CAPTURE_KEYS = ('schema', 'marker', 'rootSeed', 'layoutSeed', 'interpretation',
                'policyVersion', 'densityVersion', 'candidate', 'algorithmCandidate',
                'mode', 'referenceAvailable', 'expectedPartCount', 'lifecycle', 'bounds',
                'status', 'reasonCodes', 'sizing', 'sizingEqual', 'outline', 'compaction',
                'firstDivergence', 'items', 'firstSelectedFootprintDivergence',
                'divergenceVisits')
LIFECYCLE_KEYS = ('started', 'stopped', 'planBeginCount', 'planEndCount',
                  'planErrorCount', 'planErrorType', 'planBegan', 'planEnded')
BOUNDS_KEYS = ('maxItems', 'itemCount', 'maxVisits', 'actualVisitCount', 'itemOverflow',
               'visitOverflow', 'maxExportVisitsPerVersion', 'exportOverflow')
SIZING_KEYS = ('sourceCount', 'demandArea', 'courtyardArea', 'maxWidth', 'maxHeight',
               'anchorWidth')
OUTLINE_KEYS = ('targetArea', 'aspectIndex', 'aspectBits', 'compactFactorBits',
                'spacingFactorBits', 'contentWidth', 'contentHeight', 'outline',
                'outlineArea', 'boundaryX', 'boundaryFirstDemandBits',
                'boundaryTotalDemandBits', 'boundaryFractionBits')
RECT_KEYS = ('x', 'y', 'w', 'h')
ITEM_KEYS = ('componentId', 'declarationOrdinal', 'domainId', 'regionId', 'anchor',
             'declaredAnchor', 'packageId', 'canonicalPassive', 'passivePolicyEligible',
             'sourceGeometryKey', 'sourceGeometryFingerprint', 'sourceRotation', 'sourceSide',
             'sourceX', 'sourceY', 'sourceWidth', 'sourceHeight', 'accessMargin',
             'alternateGeometryKey', 'alternateGeometryFingerprint', 'alternateRotation',
             'envelopeTOP0', 'envelopeTOP90', 'sizingDemandArea', 'sizingEnvelopeWidth',
             'sizingEnvelopeHeight', 'placeOrdinal', 'orderKind', 'evaluationDelta', 'prefix',
             'guidance', 'selectedSearch', 'fallback', 'poseStats', 'referenceOrigin')
SELECTED_KEYS = ('selected', 'x', 'y', 'rotation', 'side', 'geometryKey',
                 'geometryFingerprint', 'scoreBits', 'fallbackUsed', 'visitCount', 'selectedKey')
GUIDANCE_KEYS = ('hintXBits', 'hintYBits', 'connectedX', 'connectedY',
                 'preferredBeforeGroupX', 'preferredBeforeGroupY', 'preferredX', 'preferredY',
                 'group', 'direction', 'baseX', 'baseY', 'boundaryPackage', 'boundaryX',
                 'support', 'vacancies', 'vacancyPreference', 'vacancyPocket', 'sourceEnvelope')
SUPPORT_KEYS = ('ownerId', 'firstPadId', 'secondPadId', 'targetX', 'targetY',
                'firstX', 'firstY', 'secondX', 'secondY')
POSE_STAT_KEYS = ('visits', 'legal', 'scored', 'minimumScoreBits', 'minimumVisitKey',
                  'incumbentUpdates', 'ringVisits', 'ringLegal', 'ringScored',
                  'fallbackVisits', 'fallbackLegal', 'fallbackScored', 'firstFallbackLegalKey')
POSES = ('DEG_0', 'DEG_90', 'DEG_180', 'DEG_270')
REFERENCE_POSE_KEYS = {rotation: 'trialSameOrigin' + rotation.replace('_', '')
                       for rotation in POSES}
EVENT_KEYS = ('visitKey', 'kind', 'ordinal', 'ring', 'index', 'fallbackOrdinal',
              'requestedX', 'requestedY', 'x', 'y', 'rotation', 'geometryKey',
              'legal', 'scored', 'scoreBits', 'incumbentUpdate')
FIRST_DIVERGENCE_KEYS = ('stage', 'componentId', 'visitKey')
DIVERGENCE_KEYS = ('componentId', 'oldPlaceOrdinal', 'trialPlaceOrdinal',
                   'oldSelectedKey', 'trialSelectedKey', 'sourceGeometryEqual',
                   'sizingEqual', 'outlineEqual', 'boundaryEqual', 'samePlaceOrdinal',
                   'prefixEqual', 'guidanceEqual', 'comparable', 'reasonCodes',
                   'oldVisitCount', 'trialVisitCount', 'visitArraysComplete', 'exportOverflow')
REFERENCE_KEYS = ('itemFound', 'oldSelectedRotation', 'oldSelectedX', 'oldSelectedY',
                  'oldSelectedGeometryKey', 'trialSameOriginDEG0', 'trialSameOriginDEG90',
                  'trialSameOriginDEG180', 'trialSameOriginDEG270',
                  'trialOldGeometryOriginVisits', 'trialOldGeometryOriginVisited')
PLAN_KEYS = ('candidate', 'demandArea', 'courtyardArea', 'evaluations', 'outline', 'footprints')
PLAN_FOOTPRINT_KEYS = ('componentId', 'packageId', 'geometryVariantKey',
                       'geometryTransformKey', 'x', 'y', 'width', 'height',
                       'rotation', 'side', 'selectedKey', 'geometryFingerprint')
PASSIVE_PACKAGES = {'AXIAL_RESISTOR', 'AXIAL_DIODE',
                    'RADIAL_ELECTROLYTIC_CAPACITOR', 'RADIAL_CERAMIC_CAPACITOR'}
STAGES = ('sizing', 'outline', 'boundary', 'source-geometry', 'item-order',
          'placed-prefix', 'guidance', 'visit-sequence', 'visit-template', 'requested-origin',
          'grid-clipping', 'fit-result', 'score-result', 'incumbent-update',
          'selected-footprint', 'compaction-output')
FAILURE_LINE = re.compile(
    r'^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|'
    r'AssertionError\b|BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|'
    r'CURRENT_CONTRACT_CLEANUP:)', re.IGNORECASE)


def _prior_reader():
    path = HERE.parent / 'passive-pose-policy-r1' / 'assess.py'
    content = path.read_bytes()
    assert hashlib.sha256(content).hexdigest() == PRIOR_ASSESS_SHA256, \
        'Hash-bound passive-pose-policy source reader changed'
    spec = importlib.util.spec_from_file_location('passive_pose_policy_reader_v1', path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_PRIOR = _prior_reader()


def baselines():
    """Return the fourteen sealed accepted15 source rows via the pinned reader."""
    return _PRIOR.baselines()


def route_baselines():
    copy_inputs = json.loads((HERE / 'copy-inputs.json').read_text(encoding='utf-8'))
    assert type(copy_inputs) is dict and copy_inputs.get('status') == 'PASS'
    assert copy_inputs.get('routeBaselinesSha256') == ROUTE_BASELINES_SHA256, \
        'Copy-input manifest does not bind the routed baseline'
    path = HERE / 'route-baselines.json'
    raw = path.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == ROUTE_BASELINES_SHA256, \
        'Sealed four-case routed baseline changed'
    document = json.loads(raw.decode('utf-8'))
    assert type(document) is dict and tuple(document.keys()) == ('rows', 'origin')
    assert type(document.get('rows')) is list
    rows = document['rows']
    assert len(rows) == 4 and all(type(row) is dict for row in rows)
    assert [(row.get('seed'), row.get('version')) for row in rows] == list(CASES)
    for row in rows:
        assert type(row) is dict and tuple(row.keys()) == ROUTE_FIELDS, \
            'Routed baseline schema/order changed'
        assert row['status'] == 'PASS' and row['density'] == 14
        assert type(row['elapsedMillis']) is int and 0 < row['elapsedMillis'] <= 60000
    return rows


def _suite_pairs(suites):
    assert type(suites) is list and suites
    pairs = []
    for item in suites:
        assert type(item) in (list, tuple) and len(item) == 2
        name, marker = item
        assert type(name) is str and re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', name)
        assert type(marker) is str and marker and '\n' not in marker and '\r' not in marker
        pairs.append((name, marker))
    assert len({name for name, _ in pairs}) == len(pairs)
    assert len({marker for _, marker in pairs}) == len(pairs)
    return pairs


def phase_suites_path(phase):
    assert phase in ('unit', 'routes')
    return HERE / ('unit-suites.json' if phase == 'unit' else 'route-suites.json')


def extract(text, prefix):
    assert type(text) is str and type(prefix) is str
    rows = []
    for line in text.splitlines():
        if line.startswith(prefix):
            rows.append(json.loads(line[len(prefix):], object_pairs_hook=_unique_object))
    return rows


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, 'Duplicate JSON object key: ' + key
        result[key] = value
    return result


def _exact_keys(value, expected, label):
    assert type(value) is dict and tuple(value.keys()) == tuple(expected), \
        label + ' field schema/order changed'


def _integer(value, label, minimum=None, maximum=None):
    assert type(value) is int, label + ' must be a JSON integer'
    if minimum is not None:
        assert value >= minimum, label + ' is below its minimum'
    if maximum is not None:
        assert value <= maximum, label + ' exceeds its maximum'
    return value


def _boolean(value, label):
    assert type(value) is bool, label + ' must be a JSON boolean'
    return value


def _text(value, label, nonempty=True):
    assert type(value) is str and (not nonempty or value), label + ' must be a string'
    return value


def _decimal_text(value, label, minimum=None):
    assert type(value) is str and value and len(value) <= 128, label + ' must be a bounded decimal string'
    try:
        parsed = Decimal(value)
    except InvalidOperation:
        raise AssertionError(label + ' must be a finite decimal string')
    assert parsed.is_finite(), label + ' must be finite'
    if minimum is not None:
        assert parsed >= Decimal(str(minimum)), label + ' is below its minimum'
    return parsed


def _double_bits(value, label, allow_nonfinite=False):
    assert type(value) is str and re.fullmatch(r'[0-9a-f]{16}', value), \
        label + ' must be sixteen lowercase hexadecimal IEEE-754 bits'
    number = struct.unpack('>d', bytes.fromhex(value))[0]
    assert allow_nonfinite or math.isfinite(number), label + ' encodes a nonfinite double'
    return number


def _finite_json(value, label):
    """Reject nonfinite or non-JSON nested guidance/geometry values."""
    if value is None or type(value) in (str, bool, int):
        return
    if type(value) is float:
        assert math.isfinite(value), label + ' contains a nonfinite number'
        return
    if type(value) is list:
        for index, child in enumerate(value):
            _finite_json(child, label + '[' + str(index) + ']')
        return
    if type(value) is dict:
        assert all(type(key) is str for key in value)
        for key, child in value.items():
            _finite_json(child, label + '.' + key)
        return
    raise AssertionError(label + ' contains a non-JSON value')


def _long_text(value, label):
    assert type(value) is str and re.fullmatch(r'-?(0|[1-9][0-9]*)', value), \
        label + ' must be a canonical signed decimal string'
    parsed = int(value)
    assert str(parsed) == value and -(1 << 63) <= parsed < (1 << 63), \
        label + ' is outside signed-long range or noncanonical'
    return parsed


def _number(value, label):
    assert type(value) in (int, float) and math.isfinite(value), label + ' must be finite numeric JSON'
    return value


def _rect(value, label):
    _exact_keys(value, RECT_KEYS, label)
    for key in RECT_KEYS:
        _integer(value[key], label + '.' + key)
    assert value['w'] > 0 and value['h'] > 0, label + ' must have positive dimensions'


def _geometry_key(value, label, package, rotation, side, width, height):
    _text(value, label)
    pieces = value.split('|')
    assert len(pieces) == 7, label + ' geometry key field count changed'
    assert pieces[0] == package and pieces[4] == rotation and pieces[5] == side, \
        label + ' package/pose does not match its physical snapshot'
    assert pieces[6] == str(width) + 'x' + str(height), \
        label + ' dimensions do not match its physical snapshot'


def _snapshot_selected_key(item):
    """Return FootprintSnapshot.selectedKey(), which omits contract version."""
    selected = item['selectedSearch']
    pieces = selected['geometryKey'].split('|')
    assert len(pieces) == 7
    snapshot_geometry = '|'.join(pieces[:3] + pieces[4:])
    return snapshot_geometry + '@' + str(selected['x']) + ',' + str(selected['y'])


def _validate_guidance(value, label):
    _exact_keys(value, GUIDANCE_KEYS, label)
    _finite_json(value, label)
    for key in ('hintXBits', 'hintYBits'):
        if value[key] is not None:
            _double_bits(value[key], label + '.' + key)
    support = value['support']
    if support is not None:
        _exact_keys(support, SUPPORT_KEYS, label + '.support')
        for key in ('ownerId', 'firstPadId', 'secondPadId'):
            _text(support[key], label + '.support.' + key)
        for key in ('targetX', 'targetY', 'firstX', 'firstY', 'secondX', 'secondY'):
            _integer(support[key], label + '.support.' + key)
    for key in ('connectedX', 'connectedY', 'preferredBeforeGroupX',
                'preferredBeforeGroupY', 'preferredX', 'preferredY', 'baseX', 'baseY',
                'boundaryX'):
        _integer(value[key], label + '.' + key)
    _integer(value['direction'], label + '.direction', minimum=-1, maximum=1)
    _boolean(value['boundaryPackage'], label + '.boundaryPackage')
    for key in ('group', 'vacancyPocket'):
        if value[key] is not None:
            _rect(value[key], label + '.' + key)
    _rect(value['sourceEnvelope'], label + '.sourceEnvelope')
    if value['vacancyPreference'] is not None:
        _exact_keys(value['vacancyPreference'], ('x', 'y'), label + '.vacancyPreference')
        _integer(value['vacancyPreference']['x'], label + '.vacancyPreference.x')
        _integer(value['vacancyPreference']['y'], label + '.vacancyPreference.y')
    assert type(value['vacancies']) is list
    for index, vacancy in enumerate(value['vacancies']):
        point = label + '.vacancies[' + str(index) + ']'
        _exact_keys(vacancy, ('x', 'y'), point)
        _integer(vacancy['x'], point + '.x')
        _integer(vacancy['y'], point + '.y')


def _validate_plan(value, label):
    if value is None:
        return
    _exact_keys(value, PLAN_KEYS, label)
    _integer(value['candidate'], label + '.candidate', minimum=0)
    for key in ('demandArea', 'courtyardArea'):
        _decimal_text(value[key], label + '.' + key, minimum=0)
    _integer(value['evaluations'], label + '.evaluations', minimum=0)
    _rect(value['outline'], label + '.outline')
    assert type(value['footprints']) is list
    seen = set()
    for index, footprint in enumerate(value['footprints']):
        where = label + '.footprints[' + str(index) + ']'
        _exact_keys(footprint, PLAN_FOOTPRINT_KEYS, where)
        _text(footprint['componentId'], where + '.componentId')
        assert footprint['componentId'] not in seen, where + ' duplicate component'
        seen.add(footprint['componentId'])
        _text(footprint['packageId'], where + '.packageId')
        _text(footprint['geometryVariantKey'], where + '.geometryVariantKey')
        _text(footprint['geometryTransformKey'], where + '.geometryTransformKey')
        for key in ('x', 'y', 'width', 'height'):
            _integer(footprint[key], where + '.' + key)
        assert footprint['width'] > 0 and footprint['height'] > 0
        _text(footprint['rotation'], where + '.rotation')
        _text(footprint['side'], where + '.side')
        _text(footprint['selectedKey'], where + '.selectedKey')
        _text(footprint['geometryFingerprint'], where + '.geometryFingerprint')
        expected_key = (footprint['packageId'] + '|' + footprint['geometryVariantKey'] + '|' +
                        footprint['geometryTransformKey'] + '|' + footprint['rotation'] + '|' +
                        footprint['side'] + '|' + str(footprint['width']) + 'x' +
                        str(footprint['height']) + '@' + str(footprint['x']) + ',' +
                        str(footprint['y']))
        assert footprint['selectedKey'] == expected_key, where + ' snapshot selectedKey changed'


def _validate_pose_stat(value, label):
    _exact_keys(value, POSE_STAT_KEYS, label)
    counts = {}
    for key in ('visits', 'legal', 'scored', 'incumbentUpdates', 'ringVisits',
                'ringLegal', 'ringScored', 'fallbackVisits', 'fallbackLegal',
                'fallbackScored'):
        counts[key] = _integer(value[key], label + '.' + key, minimum=0)
    assert counts['legal'] <= counts['visits'] and counts['scored'] <= counts['legal']
    assert counts['ringVisits'] + counts['fallbackVisits'] == counts['visits']
    assert counts['ringLegal'] + counts['fallbackLegal'] == counts['legal']
    assert counts['ringScored'] + counts['fallbackScored'] == counts['scored']
    assert counts['ringLegal'] <= counts['ringVisits'] and counts['fallbackLegal'] <= counts['fallbackVisits']
    assert counts['ringScored'] <= counts['ringLegal'] and counts['fallbackScored'] <= counts['fallbackLegal']
    assert counts['incumbentUpdates'] <= counts['scored']
    if counts['scored'] == 0:
        assert value['minimumScoreBits'] is None and value['minimumVisitKey'] is None
    else:
        _double_bits(value['minimumScoreBits'], label + '.minimumScoreBits')
        _text(value['minimumVisitKey'], label + '.minimumVisitKey')
    if counts['fallbackLegal'] == 0:
        assert value['firstFallbackLegalKey'] is None
    else:
        _text(value['firstFallbackLegalKey'], label + '.firstFallbackLegalKey')
    return counts


def _validate_item(item, index, expected_id, expected_package, policy_version, label):
    where = label + '.items[' + str(index) + ']'
    _exact_keys(item, ITEM_KEYS, where)
    assert item['componentId'] == expected_id and item['packageId'] == expected_package, \
        where + ' does not match hash-bound component inventory'
    assert item['declarationOrdinal'] == index, where + ' declaration ordinal is not lexicographic ID order'
    _text(item['domainId'], where + '.domainId', nonempty=False)
    _text(item['regionId'], where + '.regionId', nonempty=False)
    assert item['anchor'] in ('NONE', 'LEFT', 'RIGHT', 'EDGE'), where + '.anchor enum changed'
    assert item['declaredAnchor'] in ('NONE', 'LEFT', 'RIGHT', 'EDGE'), where + '.declaredAnchor enum changed'
    canonical = item['packageId'] in PASSIVE_PACKAGES
    assert _boolean(item['canonicalPassive'], where + '.canonicalPassive') == canonical
    eligible = _boolean(item['passivePolicyEligible'], where + '.passivePolicyEligible')
    assert not eligible or (policy_version == 1 and canonical and item['anchor'] == 'NONE'), \
        where + ' policy eligibility exceeds the bounded passive/nonanchor branch'
    for key in ('sourceRotation', 'alternateRotation'):
        if item[key] is not None:
            assert item[key] in POSES, where + '.' + key + ' is not a supported TOP rotation'
    assert item['sourceSide'] == 'TOP', where + ' source is not on the supported top side'
    source_rotation = item['sourceRotation']
    assert source_rotation is not None
    for key in ('sourceX', 'sourceY', 'sourceWidth', 'sourceHeight', 'accessMargin',
                'sizingEnvelopeWidth', 'sizingEnvelopeHeight', 'placeOrdinal', 'evaluationDelta'):
        _integer(item[key], where + '.' + key)
    assert item['sourceWidth'] > 0 and item['sourceHeight'] > 0
    assert item['sizingEnvelopeWidth'] > 0 and item['sizingEnvelopeHeight'] > 0
    _text(item['sourceGeometryFingerprint'], where + '.sourceGeometryFingerprint')
    _geometry_key(item['sourceGeometryKey'], where + '.sourceGeometryKey', item['packageId'],
                  source_rotation, item['sourceSide'], item['sourceWidth'], item['sourceHeight'])
    _text(item['alternateGeometryFingerprint'], where + '.alternateGeometryFingerprint', nonempty=False) \
        if item['alternateGeometryFingerprint'] is not None else None
    if eligible:
        assert item['canonicalPassive'] and source_rotation == 'DEG_0'
        assert item['alternateRotation'] == 'DEG_90'
        assert item['alternateGeometryKey'] is not None and item['alternateGeometryFingerprint'] is not None
        assert item['envelopeTOP90'] is not None
        alt_width, alt_height = _geometry_key_dimensions(item['alternateGeometryKey'], where + '.alternateGeometryKey')
        _geometry_key(item['alternateGeometryKey'], where + '.alternateGeometryKey', item['packageId'],
                      item['alternateRotation'], item['sourceSide'], alt_width, alt_height)
    else:
        assert item['alternateRotation'] is None and item['alternateGeometryKey'] is None
        assert item['alternateGeometryFingerprint'] is None and item['envelopeTOP90'] is None
    if canonical:
        assert source_rotation == 'DEG_0' and item['sourceSide'] == 'TOP'
    _rect(item['envelopeTOP0'], where + '.envelopeTOP0')
    if item['envelopeTOP90'] is not None:
        _rect(item['envelopeTOP90'], where + '.envelopeTOP90')
    _decimal_text(item['sizingDemandArea'], where + '.sizingDemandArea', minimum=0)
    _integer(item['placeOrdinal'], where + '.placeOrdinal', minimum=0)
    assert item['orderKind'] in ('anchor', 'remaining')
    assert type(item['prefix']) is list
    prefix_ids = []
    for prefix_index, entry in enumerate(item['prefix']):
        _exact_keys(entry, ('componentId', 'selectedKey'), where + '.prefix[' + str(prefix_index) + ']')
        prefix_ids.append(_text(entry['componentId'], where + '.prefix componentId'))
        _text(entry['selectedKey'], where + '.prefix selectedKey')
    assert len(prefix_ids) == len(set(prefix_ids)) and item['componentId'] not in prefix_ids
    _validate_guidance(item['guidance'], where + '.guidance')
    selected = item['selectedSearch']
    _exact_keys(selected, SELECTED_KEYS, where + '.selectedSearch')
    _boolean(selected['selected'], where + '.selectedSearch.selected')
    assert selected['selected'] is True, where + ' has no selected geometry'
    for key in ('x', 'y'):
        _integer(selected[key], where + '.selectedSearch.' + key)
    assert selected['rotation'] in POSES and selected['side'] == 'TOP'
    assert selected['rotation'] in (('DEG_0', 'DEG_90') if eligible else (source_rotation,)), \
        where + ' selected rotation is outside its source/alternate poses'
    _text(selected['geometryFingerprint'], where + '.selectedSearch.geometryFingerprint')
    _geometry_key(selected['geometryKey'], where + '.selectedSearch.geometryKey', item['packageId'],
                  selected['rotation'], selected['side'],
                  _geometry_key_dimensions(selected['geometryKey'], where + '.selectedSearch.geometryKey')[0],
                  _geometry_key_dimensions(selected['geometryKey'], where + '.selectedSearch.geometryKey')[1])
    expected_selected_geometry = (item['sourceGeometryKey'] if selected['rotation'] == source_rotation
                                  else item['alternateGeometryKey'])
    assert selected['geometryKey'] == expected_selected_geometry, \
        where + ' selected geometry key does not match its declared source/alternate template'
    _boolean(selected['fallbackUsed'], where + '.selectedSearch.fallbackUsed')
    _integer(selected['visitCount'], where + '.selectedSearch.visitCount', minimum=0)
    _text(selected['selectedKey'], where + '.selectedSearch.selectedKey')
    assert selected['selectedKey'] == selected['geometryKey'] + '@' + str(selected['x']) + ',' + str(selected['y'])
    _double_bits(selected['scoreBits'], where + '.selectedSearch.scoreBits')
    fallback = item['fallback']
    if fallback is not None:
        _exact_keys(fallback, ('minX', 'maxX', 'minY', 'maxY', 'columns', 'rows', 'total', 'start'),
                    where + '.fallback')
        for key in ('minX', 'maxX', 'minY', 'maxY', 'columns', 'rows', 'start'):
            _integer(fallback[key], where + '.fallback.' + key)
        _decimal_text(fallback['total'], where + '.fallback.total', minimum=0)
        assert fallback['columns'] >= 0 and fallback['rows'] >= 0
    stats = item['poseStats']
    _exact_keys(stats, POSES, where + '.poseStats')
    counts = [_validate_pose_stat(stats[rotation], where + '.poseStats.' + rotation) for rotation in POSES]
    assert sum(row['visits'] for row in counts) == selected['visitCount'], \
        where + ' selected visit count differs from per-pose counters'
    scored_minima = [_double_bits(stats[rotation]['minimumScoreBits'], where + '.minimumScoreBits')
                     for rotation in POSES if stats[rotation]['minimumScoreBits'] is not None]
    assert scored_minima and min(scored_minima) == _double_bits(selected['scoreBits'], where + '.selectedSearch.scoreBits'), \
        where + ' selected score does not equal the best observed per-pose score'
    reference = item['referenceOrigin']
    if policy_version == 0:
        assert reference is None
        return {'counts': counts, 'selected': selected}
    _exact_keys(reference, REFERENCE_KEYS, where + '.referenceOrigin')
    _boolean(reference['itemFound'], where + '.referenceOrigin.itemFound')
    if reference['itemFound']:
        assert reference['oldSelectedRotation'] in POSES
        for key in ('oldSelectedX', 'oldSelectedY'):
            _integer(reference[key], where + '.referenceOrigin.' + key)
        _text(reference['oldSelectedGeometryKey'], where + '.referenceOrigin.oldSelectedGeometryKey')
    else:
        assert reference['oldSelectedRotation'] is None and reference['oldSelectedX'] is None
        assert reference['oldSelectedY'] is None and reference['oldSelectedGeometryKey'] is None
    for key in ('trialSameOriginDEG0', 'trialSameOriginDEG90', 'trialSameOriginDEG180',
                'trialSameOriginDEG270', 'trialOldGeometryOriginVisits'):
        _integer(reference[key], where + '.referenceOrigin.' + key, minimum=0)
    _boolean(reference['trialOldGeometryOriginVisited'], where + '.referenceOrigin.trialOldGeometryOriginVisited')
    assert reference['trialOldGeometryOriginVisited'] == (reference['trialOldGeometryOriginVisits'] > 0)
    return {'counts': counts, 'selected': selected}


def _geometry_key_dimensions(value, label):
    pieces = value.split('|') if type(value) is str else []
    assert len(pieces) == 7, label + ' geometry key field count changed'
    match = re.fullmatch(r'([1-9][0-9]*)x([1-9][0-9]*)', pieces[6])
    assert match is not None, label + ' geometry key dimensions changed'
    return (int(match.group(1)), int(match.group(2)))


def _validate_event(event, label, eligible, source_rotation, ring_slots,
                    expected_geometry_key=None):
    _exact_keys(event, EVENT_KEYS, label)
    assert ring_slots in (5, 6), label + ' has unsupported ring slot count'
    _text(event['visitKey'], label + '.visitKey')
    assert event['kind'] in ('ring', 'fallback')
    _integer(event['ordinal'], label + '.ordinal', minimum=0)
    if event['kind'] == 'ring':
        _integer(event['ring'], label + '.ring', minimum=0)
        _integer(event['index'], label + '.index', minimum=0, maximum=ring_slots - 1)
        assert event['ordinal'] == event['ring'] * ring_slots + event['index']
        assert event['visitKey'] == 'ring:' + str(event['ring']) + ':' + str(event['index'])
        assert event['fallbackOrdinal'] is None
    else:
        assert event['ring'] is None and event['index'] is None
        _integer(event['fallbackOrdinal'], label + '.fallbackOrdinal', minimum=0)
        assert event['ordinal'] == event['fallbackOrdinal']
        assert event['visitKey'] == 'fallback:' + str(event['fallbackOrdinal'])
    for key in ('requestedX', 'requestedY', 'x', 'y'):
        _integer(event[key], label + '.' + key)
    assert event['rotation'] in POSES
    if eligible:
        assert event['rotation'] == ('DEG_0' if event['ordinal'] % 2 == 0 else 'DEG_90'), \
            label + ' eligible passive pose does not follow the actual ordinal schedule'
    else:
        assert event['rotation'] == source_rotation, label + ' ineligible/provider pose was not preserved'
    _text(event['geometryKey'], label + '.geometryKey')
    if expected_geometry_key is not None:
        assert event['geometryKey'] == expected_geometry_key, label + ' event geometry differs from its observed pose'
    _boolean(event['legal'], label + '.legal')
    _boolean(event['scored'], label + '.scored')
    _boolean(event['incumbentUpdate'], label + '.incumbentUpdate')
    if event['scored']:
        assert event['legal'] is True and event['scoreBits'] is not None
        _double_bits(event['scoreBits'], label + '.scoreBits')
    else:
        assert event['scoreBits'] is None and event['incumbentUpdate'] is False
    if event['legal']:
        assert event['scored'] is True, label + ' legal fit lacks its actual finite score'


def _validate_trace_events(events, item, label):
    assert type(events) is list
    selected = item['selectedSearch']
    eligible = item['passivePolicyEligible']
    source_rotation = item['sourceRotation']
    assert item['orderKind'] in ('anchor', 'remaining')
    ring_slots = 5 if item['orderKind'] == 'anchor' else 6
    assert len(events) == selected['visitCount'] and len(events) <= 512
    prior_ordinals = []
    per_pose_events = {rotation: [] for rotation in POSES}
    running_minimum = None
    last_incumbent = None
    for index, event in enumerate(events):
        if eligible:
            expected_key = (item['sourceGeometryKey'] if event['ordinal'] % 2 == 0
                            else item['alternateGeometryKey'])
        else:
            expected_key = item['sourceGeometryKey']
        _validate_event(event, label + '[' + str(index) + ']', eligible,
                        source_rotation, ring_slots, expected_key)
        if event['scored']:
            score = _double_bits(event['scoreBits'], label + '.scoreBits')
            expected_update = running_minimum is None or score < running_minimum
            assert event['incumbentUpdate'] is expected_update, \
                label + ' incumbent update does not follow strict observed-score improvement'
            if expected_update:
                running_minimum = score
                last_incumbent = event
        prior_ordinals.append(event['ordinal'])
        per_pose_events[event['rotation']].append(event)
    ring_ordinals = [event['ordinal'] for event in events if event['kind'] == 'ring']
    fallback_ordinals = [event['ordinal'] for event in events if event['kind'] == 'fallback']
    assert ring_ordinals == sorted(ring_ordinals) and fallback_ordinals == sorted(fallback_ordinals)
    assert not any(event['kind'] == 'ring' for event in events[
        next((i for i, event in enumerate(events) if event['kind'] == 'fallback'), len(events)):])
    assert len(prior_ordinals) == len(set((event['kind'], event['ordinal']) for event in events)), \
        label + ' repeated a visit ordinal'
    for rotation in POSES:
        observed = per_pose_events[rotation]
        stats = item['poseStats'][rotation]
        assert stats['visits'] == len(observed)
        assert stats['legal'] == sum(1 for event in observed if event['legal'])
        assert stats['scored'] == sum(1 for event in observed if event['scored'])
        assert stats['incumbentUpdates'] == sum(1 for event in observed if event['incumbentUpdate'])
        assert stats['ringVisits'] == sum(1 for event in observed if event['kind'] == 'ring')
        assert stats['fallbackVisits'] == sum(1 for event in observed if event['kind'] == 'fallback')
        assert stats['ringLegal'] == sum(1 for event in observed if event['kind'] == 'ring' and event['legal'])
        assert stats['fallbackLegal'] == sum(1 for event in observed if event['kind'] == 'fallback' and event['legal'])
        assert stats['ringScored'] == sum(1 for event in observed if event['kind'] == 'ring' and event['scored'])
        assert stats['fallbackScored'] == sum(1 for event in observed if event['kind'] == 'fallback' and event['scored'])
        scored = [event for event in observed if event['scored']]
        if scored:
            minimum = min(_double_bits(event['scoreBits'], label + '.scoreBits') for event in scored)
            first = next(event for event in scored if _double_bits(event['scoreBits'], label + '.scoreBits') == minimum)
            assert stats['minimumScoreBits'] == first['scoreBits']
            assert stats['minimumVisitKey'] == first['visitKey']
        else:
            assert stats['minimumScoreBits'] is None and stats['minimumVisitKey'] is None
        fallback_legal = next((event for event in observed if event['kind'] == 'fallback' and event['legal']), None)
        assert stats['firstFallbackLegalKey'] == (fallback_legal['visitKey'] if fallback_legal else None)
    selected_event = next((event for event in events if event['legal'] and event['scored'] and
                           event['geometryKey'] == selected['geometryKey'] and
                           event['rotation'] == selected['rotation'] and
                           event['x'] == selected['x'] and event['y'] == selected['y'] and
                           event['scoreBits'] == selected['scoreBits']), None)
    assert selected_event is not None, label + ' selected geometry/origin/score is not backed by an actual legal scored event'
    assert selected['fallbackUsed'] is (selected_event['kind'] == 'fallback')
    assert selected_event is last_incumbent, label + ' selected event is not the final strict-score incumbent'


def _validate_capture(capture, label, source_binding=None, protocol_sample=False):
    _finite_json(capture, label)
    _exact_keys(capture, CAPTURE_KEYS, label)
    assert capture['schema'] == 'pcb-passive-choice-observer-v1'
    assert capture['marker'] == 'PCB_PASSIVE_CHOICE_CAPTURE_V1'
    root_text = capture['rootSeed']
    _long_text(root_text, label + '.rootSeed')
    _long_text(capture['layoutSeed'], label + '.layoutSeed')
    _integer(capture['interpretation'], label + '.interpretation', minimum=0)
    _integer(capture['policyVersion'], label + '.policyVersion')
    _integer(capture['densityVersion'], label + '.densityVersion')
    _integer(capture['candidate'], label + '.candidate')
    _integer(capture['algorithmCandidate'], label + '.algorithmCandidate')
    assert capture['policyVersion'] in (0, 1)
    assert capture['densityVersion'] == 14
    assert capture['candidate'] == 1 and capture['algorithmCandidate'] == 0
    assert capture['mode'] in ('old', 'trial')
    assert capture['policyVersion'] == (0 if capture['mode'] == 'old' else 1)
    if capture['mode'] == 'old':
        assert capture['interpretation'] == 15
    else:
        assert capture['interpretation'] == 16
    reference_available = _boolean(capture['referenceAvailable'], label + '.referenceAvailable')
    assert reference_available is (capture['mode'] == 'trial')
    expected_count = _integer(capture['expectedPartCount'], label + '.expectedPartCount', minimum=0)
    life = capture['lifecycle']
    _exact_keys(life, LIFECYCLE_KEYS, label + '.lifecycle')
    for key in ('started', 'stopped', 'planBegan', 'planEnded'):
        _boolean(life[key], label + '.lifecycle.' + key)
    for key in ('planBeginCount', 'planEndCount', 'planErrorCount'):
        _integer(life[key], label + '.lifecycle.' + key, minimum=0)
    if life['planErrorType'] is not None:
        _text(life['planErrorType'], label + '.lifecycle.planErrorType')
    lifecycle_complete = (life['started'] and life['stopped'] and life['planBeginCount'] == 1 and
                          life['planEndCount'] == 1 and life['planErrorCount'] == 0 and
                          life['planBegan'] and life['planEnded'] and life['planErrorType'] is None)
    bounds = capture['bounds']
    _exact_keys(bounds, BOUNDS_KEYS, label + '.bounds')
    for key in ('maxItems', 'itemCount', 'maxVisits', 'actualVisitCount', 'maxExportVisitsPerVersion'):
        _integer(bounds[key], label + '.bounds.' + key, minimum=0)
    for key in ('itemOverflow', 'visitOverflow', 'exportOverflow'):
        _boolean(bounds[key], label + '.bounds.' + key)
    assert bounds['maxItems'] == 128 and bounds['maxVisits'] == 60000
    assert bounds['maxExportVisitsPerVersion'] == 512
    assert bounds['itemCount'] <= bounds['maxItems']
    assert bounds['actualVisitCount'] <= bounds['maxVisits']
    items = capture['items']
    assert type(items) is list and len(items) == bounds['itemCount']
    status = capture['status']
    assert status in ('COMPLETE', 'INCONCLUSIVE')
    reason_codes = capture['reasonCodes']
    assert type(reason_codes) is list and all(type(code) is str and code for code in reason_codes)
    assert len(reason_codes) == len(set(reason_codes))
    sizing_frame, outline_frame, compaction_frame = (capture['sizing'], capture['outline'],
                                                      capture['compaction'])
    compaction_complete = (type(compaction_frame) is dict and
                           compaction_frame.get('pre') is not None and
                           compaction_frame.get('post') is not None)
    required_frames = (type(sizing_frame) is dict and type(outline_frame) is dict and
                       compaction_complete)
    bounded_complete = (lifecycle_complete and not reason_codes and
                        not bounds['itemOverflow'] and not bounds['visitOverflow'] and
                        not bounds['exportOverflow'] and bounds['itemCount'] == expected_count and
                        required_frames)
    if status == 'INCONCLUSIVE':
        assert not bounded_complete, label + ' claims INCONCLUSIVE without a stated incompleteness'
        if source_binding is not None:
            assert root_text in ('42', '3') and root_text == source_binding['seed']
            assert capture['expectedPartCount'] == source_binding['parts']
            assert capture['layoutSeed'] == source_binding['layoutSeed']
            inventory = source_binding['sourceComponents']
            assert len(items) <= len(inventory)
            for index, item in enumerate(items):
                assert type(item) is dict and item.get('componentId') == inventory[index]['componentId']
                assert item.get('packageId') == inventory[index]['packageId']
        return {'rootSeed': root_text, 'interpretation': capture['interpretation'],
                'policyVersion': capture['policyVersion'], 'mode': capture['mode'],
                'status': status, 'lifecycleComplete': lifecycle_complete,
                'itemCount': expected_count, 'actualVisitCount': bounds['actualVisitCount'],
                'firstDivergenceStage': None, 'firstSelectedComponent': None}
    assert bounded_complete, label + ' claims COMPLETE without all required bounded frames'
    _integer(capture['sizing']['sourceCount'], label + '.sizing.sourceCount', minimum=0)
    _exact_keys(capture['sizing'], SIZING_KEYS, label + '.sizing')
    for key in ('demandArea', 'courtyardArea'):
        _decimal_text(capture['sizing'][key], label + '.sizing.' + key, minimum=0)
    for key in ('maxWidth', 'maxHeight', 'anchorWidth'):
        _integer(capture['sizing'][key], label + '.sizing.' + key, minimum=0)
    assert capture['sizing']['sourceCount'] == expected_count
    if capture['sizingEqual'] is not None:
        _boolean(capture['sizingEqual'], label + '.sizingEqual')
    assert (capture['sizingEqual'] is not None) if capture['mode'] == 'trial' else \
        (capture['sizingEqual'] is None)
    outline = capture['outline']
    _exact_keys(outline, OUTLINE_KEYS, label + '.outline')
    _decimal_text(outline['targetArea'], label + '.outline.targetArea', minimum=0)
    _integer(outline['aspectIndex'], label + '.outline.aspectIndex')
    for key in ('aspectBits', 'compactFactorBits', 'spacingFactorBits'):
        _double_bits(outline[key], label + '.outline.' + key)
    for key in ('boundaryFirstDemandBits', 'boundaryTotalDemandBits', 'boundaryFractionBits'):
        if outline[key] is not None:
            _double_bits(outline[key], label + '.outline.' + key)
    for key in ('contentWidth', 'contentHeight', 'boundaryX'):
        _integer(outline[key], label + '.outline.' + key)
    _rect(outline['outline'], label + '.outline.outline')
    _decimal_text(outline['outlineArea'], label + '.outline.outlineArea', minimum=0)
    if outline['boundaryX'] < 0:
        assert all(outline[key] is None for key in
                   ('boundaryFirstDemandBits', 'boundaryTotalDemandBits', 'boundaryFractionBits'))
    else:
        assert all(outline[key] is not None for key in
                   ('boundaryFirstDemandBits', 'boundaryTotalDemandBits', 'boundaryFractionBits'))
    compaction = capture['compaction']
    _exact_keys(compaction, ('pre', 'post'), label + '.compaction')
    _validate_plan(compaction['pre'], label + '.compaction.pre')
    _validate_plan(compaction['post'], label + '.compaction.post')
    first = capture['firstDivergence']
    if first is not None:
        _exact_keys(first, FIRST_DIVERGENCE_KEYS, label + '.firstDivergence')
        assert first['stage'] in STAGES
        if first['componentId'] is not None:
            _text(first['componentId'], label + '.firstDivergence.componentId')
        if first['visitKey'] is not None:
            _text(first['visitKey'], label + '.firstDivergence.visitKey')
    if source_binding is not None:
        assert root_text in ('42', '3')
        assert root_text == source_binding['seed'] and capture['expectedPartCount'] == source_binding['parts']
        assert capture['layoutSeed'] == source_binding['layoutSeed']
        inventory = source_binding['sourceComponents']
        assert len(inventory) == expected_count
        assert len(items) <= len(inventory)
        for index, (item, expected) in enumerate(zip(items, inventory)):
            _validate_item(item, index, expected['componentId'], expected['packageId'],
                           capture['policyVersion'], label)
        if not bounds['itemOverflow'] and not bounds['visitOverflow'] and len(items) == expected_count:
            assert bounds['actualVisitCount'] == sum(
                stats['visits'] for item in items for stats in item['poseStats'].values())
    else:
        assert len(items) == expected_count
        for index, item in enumerate(items):
            expected_id = _text(item.get('componentId'), label + '.items componentId')
            expected_package = _text(item.get('packageId'), label + '.items packageId')
            _validate_item(item, index, expected_id, expected_package,
                           capture['policyVersion'], label)
    assert type(capture['firstSelectedFootprintDivergence']) in (dict, type(None))
    divergence = capture['firstSelectedFootprintDivergence']
    visits = capture['divergenceVisits']
    if capture['mode'] == 'old':
        assert divergence is None and visits is None
        assert bounds['exportOverflow'] is False
    if bounds['exportOverflow']:
        assert divergence is not None and visits is None
    if divergence is None:
        assert visits is None
    else:
        _exact_keys(divergence, DIVERGENCE_KEYS, label + '.firstSelectedFootprintDivergence')
        for key in ('componentId', 'oldSelectedKey', 'trialSelectedKey'):
            _text(divergence[key], label + '.firstSelectedFootprintDivergence.' + key)
        for key in ('oldPlaceOrdinal', 'trialPlaceOrdinal', 'oldVisitCount', 'trialVisitCount'):
            _integer(divergence[key], label + '.firstSelectedFootprintDivergence.' + key, minimum=0)
        for key in ('sourceGeometryEqual', 'sizingEqual', 'outlineEqual', 'boundaryEqual',
                    'samePlaceOrdinal', 'prefixEqual', 'guidanceEqual', 'comparable',
                    'visitArraysComplete', 'exportOverflow'):
            _boolean(divergence[key], label + '.firstSelectedFootprintDivergence.' + key)
        assert type(divergence['reasonCodes']) is list and all(type(code) is str for code in divergence['reasonCodes'])
        expected_comparable = all(divergence[key] for key in (
            'sourceGeometryEqual', 'outlineEqual', 'boundaryEqual',
            'samePlaceOrdinal', 'prefixEqual', 'guidanceEqual'))
        assert divergence['comparable'] is expected_comparable
        if visits is None:
            assert not divergence['visitArraysComplete']
            assert divergence['exportOverflow'] is True
            assert bounds['exportOverflow'] is True
            assert max(divergence['oldVisitCount'], divergence['trialVisitCount']) > \
                bounds['maxExportVisitsPerVersion']
            assert capture['status'] == 'INCONCLUSIVE'
        else:
            _exact_keys(visits, ('old', 'trial'), label + '.divergenceVisits')
            assert type(visits['old']) is list and type(visits['trial']) is list
            assert len(visits['old']) == divergence['oldVisitCount']
            assert len(visits['trial']) == divergence['trialVisitCount']
            assert len(visits['old']) <= 512 and len(visits['trial']) <= 512
            assert divergence['visitArraysComplete'] is True and divergence['exportOverflow'] is False
            assert bounds['exportOverflow'] is False
            matching = [item for item in items if item['componentId'] == divergence['componentId']]
            assert len(matching) == 1
            selected = matching[0]
            _validate_trace_events(visits['trial'], selected, label + '.divergenceVisits.trial')
            # The old trace uses the same declared source geometry and selection summary,
            # reconstructed from the paired old capture by _assess_captures.
    if source_binding is None and protocol_sample:
        assert capture['mode'] == 'old' and capture['policyVersion'] == 0
        assert capture['status'] == 'COMPLETE' and lifecycle_complete
        assert expected_count == 1 and bounds['actualVisitCount'] == 3
        assert not reference_available and not capture['reasonCodes']
    return {'rootSeed': capture['rootSeed'], 'interpretation': capture['interpretation'],
            'policyVersion': capture['policyVersion'], 'mode': capture['mode'],
            'status': capture['status'], 'lifecycleComplete': lifecycle_complete,
            'itemCount': expected_count, 'actualVisitCount': bounds['actualVisitCount'],
            'firstDivergenceStage': first['stage'] if first else None,
            'firstSelectedComponent': divergence['componentId'] if divergence else None}


def check_protocol_sample(unit_text):
    """Check the real helper serializer's single synthetic unit sample, shape only."""
    rows = extract(unit_text, PROTOCOL_SAMPLE_PREFIX)
    assert len(rows) == 1 and type(rows[0]) is dict, \
        'Exactly one serialized synthetic protocol sample is required'
    sample = rows[0]
    _validate_capture(sample, 'protocolSample', protocol_sample=True)
    assert sample['rootSeed'] == '42' and sample['layoutSeed'] == '9001'
    assert sample['mode'] == 'old' and sample['interpretation'] == 15
    assert sample['policyVersion'] == 0 and sample['densityVersion'] == 14
    assert sample['candidate'] == 1 and sample['algorithmCandidate'] == 0
    assert sample['expectedPartCount'] == 1 and sample['bounds']['itemCount'] == 1
    assert sample['bounds']['actualVisitCount'] == 3
    assert sample['lifecycle']['planBeginCount'] == sample['lifecycle']['planEndCount'] == 1
    assert sample['lifecycle']['planErrorCount'] == 0 and sample['lifecycle']['planBegan']
    assert sample['lifecycle']['planEnded'] and sample['lifecycle']['stopped']
    assert sample['reasonCodes'] == [] and not any(sample['bounds'][key]
        for key in ('itemOverflow', 'visitOverflow', 'exportOverflow'))
    assert sample['firstDivergence'] is None
    item = sample['items'][0]
    assert item['componentId'] == 'R1' and item['packageId'] == 'AXIAL_RESISTOR'
    assert item['domainId'] == 'domain' and item['regionId'] == 'region'
    assert item['sourceRotation'] == 'DEG_0' and item['sourceSide'] == 'TOP'
    assert item['sourceX'] == 100 and item['sourceY'] == 200 and item['accessMargin'] == 20
    assert item['canonicalPassive'] is True and item['passivePolicyEligible'] is False
    assert item['anchor'] == 'NONE' and item['declaredAnchor'] == 'NONE'
    assert item['referenceOrigin'] is None
    assert item['fallback'] is None
    stats = item['poseStats']
    zero = stats['DEG_0']
    assert zero['visits'] == zero['legal'] == zero['scored'] == 3
    assert zero['ringVisits'] == zero['ringLegal'] == zero['ringScored'] == 3
    assert zero['fallbackVisits'] == zero['fallbackLegal'] == zero['fallbackScored'] == 0
    assert zero['minimumVisitKey'] == 'ring:0:1'
    assert zero['incumbentUpdates'] == 2 and zero['firstFallbackLegalKey'] is None
    assert _double_bits(zero['minimumScoreBits'], 'protocolSample.minimumScoreBits') == 1.0
    assert sum(stats[rotation]['visits'] for rotation in POSES[1:]) == 0
    assert item['selectedSearch']['rotation'] == 'DEG_0'
    assert item['selectedSearch']['selected'] is True
    assert item['selectedSearch']['visitCount'] == 3
    assert item['selectedSearch']['x'] == 100 and item['selectedSearch']['y'] == 200
    assert _double_bits(item['selectedSearch']['scoreBits'],
                        'protocolSample.selectedSearch.scoreBits') == 1.0
    return {'status': 'PASS', 'sampleCount': 1,
            'schema': sample['schema'], 'marker': sample['marker'], 'syntheticOnly': True}


def _source_geometry_equal(old, trial):
    fields = ('packageId', 'sourceGeometryKey', 'sourceGeometryFingerprint',
              'sourceRotation', 'sourceSide', 'sourceWidth', 'sourceHeight')
    return all(old[key] == trial[key] for key in fields)


def _sizing_equal(old_capture, trial_capture, old_items, trial_items):
    fields = ('sourceCount', 'demandArea', 'courtyardArea', 'maxWidth', 'maxHeight', 'anchorWidth')
    aggregates = all(old_capture['sizing'][key] == trial_capture['sizing'][key] for key in fields)
    item_fields = ('sizingDemandArea', 'sizingEnvelopeWidth', 'sizingEnvelopeHeight',
                   'accessMargin', 'envelopeTOP0', 'envelopeTOP90')
    per_item = all(all(old_items[item_id][key] == trial_items[item_id][key]
                       for key in item_fields) for item_id in old_items)
    return aggregates and per_item


def _outline_equal(old, trial):
    return old['outline'] == trial['outline']


def _boundary_equal(old, trial):
    keys = ('boundaryX', 'boundaryFirstDemandBits', 'boundaryTotalDemandBits', 'boundaryFractionBits')
    return all(old['outline'][key] == trial['outline'][key] for key in keys)


def _first_event_difference(old_events, trial_events):
    count = min(len(old_events), len(trial_events))
    for index in range(count):
        old, trial = old_events[index], trial_events[index]
        if any(old[key] != trial[key] for key in ('visitKey', 'kind', 'ordinal', 'ring', 'index', 'fallbackOrdinal')):
            return {'stage': 'visit-sequence', 'visitKey': trial['visitKey']}
        if any(old[key] != trial[key] for key in ('rotation', 'geometryKey')):
            return {'stage': 'visit-template', 'visitKey': trial['visitKey']}
        if any(old[key] != trial[key] for key in ('requestedX', 'requestedY')):
            return {'stage': 'requested-origin', 'visitKey': trial['visitKey']}
        if any(old[key] != trial[key] for key in ('x', 'y')):
            return {'stage': 'grid-clipping', 'visitKey': trial['visitKey']}
        if old['legal'] != trial['legal']:
            return {'stage': 'fit-result', 'visitKey': trial['visitKey']}
        if old['scored'] != trial['scored'] or old['scoreBits'] != trial['scoreBits']:
            return {'stage': 'score-result', 'visitKey': trial['visitKey']}
        if old['incumbentUpdate'] != trial['incumbentUpdate']:
            return {'stage': 'incumbent-update', 'visitKey': trial['visitKey']}
    if len(old_events) != len(trial_events):
        return {'stage': 'visit-sequence',
                'visitKey': (trial_events[count]['visitKey'] if len(trial_events) > count else None)}
    return None


def _check_reference_origin_visits(reference, old_selected, trial_events, label):
    """Recompute old-origin matches from exported actual visits only."""
    old_x, old_y = old_selected['x'], old_selected['y']
    same_origin = {rotation: 0 for rotation in POSES}
    old_geometry = 0
    for event in trial_events:
        if event['x'] == old_x and event['y'] == old_y:
            same_origin[event['rotation']] += 1
            if event['geometryKey'] == old_selected['geometryKey']:
                old_geometry += 1
    for rotation in POSES:
        assert reference[REFERENCE_POSE_KEYS[rotation]] == same_origin[rotation], \
            label + ' reference-origin rotation count is not derived from actual exported visits'
    assert reference['trialOldGeometryOriginVisits'] == old_geometry
    assert reference['trialOldGeometryOriginVisited'] is (old_geometry > 0)


def _expected_first_divergence(old_capture, trial_capture, old_items, trial_items,
                               selected_diff, visits):
    for item_id in old_items:
        if not _source_geometry_equal(old_items[item_id], trial_items[item_id]):
            return {'stage': 'source-geometry', 'componentId': item_id, 'visitKey': None}, True
    if not _sizing_equal(old_capture, trial_capture, old_items, trial_items):
        return {'stage': 'sizing', 'componentId': None, 'visitKey': None}, True
    old_outline, trial_outline = old_capture['outline'], trial_capture['outline']
    outline_keys = ('targetArea', 'aspectIndex', 'aspectBits', 'compactFactorBits',
                    'spacingFactorBits', 'contentWidth', 'contentHeight', 'outline', 'outlineArea')
    if any(old_outline[key] != trial_outline[key] for key in outline_keys):
        return {'stage': 'outline', 'componentId': None, 'visitKey': None}, True
    if not _boundary_equal(old_capture, trial_capture):
        return {'stage': 'boundary', 'componentId': None, 'visitKey': None}, True
    trial_order = sorted(trial_items.values(), key=lambda item: item['placeOrdinal'])
    for item in trial_order:
        old = old_items[item['componentId']]
        if any(old[key] != item[key] for key in ('placeOrdinal', 'orderKind', 'anchor')):
            return {'stage': 'item-order', 'componentId': item['componentId'], 'visitKey': None}, True
    selected_id = selected_diff['componentId'] if selected_diff is not None else None
    for item in trial_order:
        old = old_items[item['componentId']]
        if old['prefix'] != item['prefix']:
            return {'stage': 'placed-prefix', 'componentId': item['componentId'], 'visitKey': None}, \
                item['placeOrdinal'] == 0
        if old['guidance'] != item['guidance']:
            return {'stage': 'guidance', 'componentId': item['componentId'], 'visitKey': None}, \
                item['placeOrdinal'] == 0
        if visits is not None and item['componentId'] == selected_id:
            event_diff = _first_event_difference(visits['old'], visits['trial'])
            if event_diff is not None:
                event_diff['componentId'] = item['componentId']
                return event_diff, item['placeOrdinal'] == 0
        if item['componentId'] == selected_id:
            return {'stage': 'selected-footprint', 'componentId': item['componentId'],
                    'visitKey': None}, item['placeOrdinal'] == 0
    if old_capture['compaction'] != trial_capture['compaction']:
        return {'stage': 'compaction-output', 'componentId': None, 'visitKey': None}, False
    return None, False


def _assess_capture_pair(old_capture, trial_capture, label):
    old_by_id = {item['componentId']: item for item in old_capture['items']}
    trial_by_id = {item['componentId']: item for item in trial_capture['items']}
    assert list(old_by_id) == list(trial_by_id), label + ' paired item inventory/order differs'
    for capture, items in ((old_capture, old_by_id), (trial_capture, trial_by_id)):
        ordinals = [item['placeOrdinal'] for item in items.values()]
        assert sorted(ordinals) == list(range(len(ordinals))), label + ' placement ordinals are not a permutation'
        ordered = sorted(items.values(), key=lambda item: item['placeOrdinal'])
        for index, item in enumerate(ordered):
            expected_prefix = [{'componentId': prior['componentId'],
                                'selectedKey': _snapshot_selected_key(prior)}
                               for prior in ordered[:index]]
            assert item['prefix'] == expected_prefix, \
                label + ' placed-prefix snapshot does not match the actual prior selected footprints'
    sizing_equal = _sizing_equal(old_capture, trial_capture, old_by_id, trial_by_id)
    assert trial_capture['sizingEqual'] is sizing_equal, label + ' top-level sizing comparison is false'
    for item_id in old_by_id:
        old, trial = old_by_id[item_id], trial_by_id[item_id]
        ref = trial['referenceOrigin']
        assert ref is not None and ref['itemFound'] is True, label + ' trial item lacks its paired old selection'
        old_selected = old['selectedSearch']
        assert ref['oldSelectedRotation'] == old_selected['rotation']
        assert ref['oldSelectedX'] == old_selected['x'] and ref['oldSelectedY'] == old_selected['y']
        assert ref['oldSelectedGeometryKey'] == old_selected['geometryKey']
        same_origin_counts = [ref[REFERENCE_POSE_KEYS[rotation]] for rotation in POSES]
        assert sum(same_origin_counts) <= trial['selectedSearch']['visitCount']
        assert all(ref[REFERENCE_POSE_KEYS[rotation]] <= trial['poseStats'][rotation]['visits']
                   for rotation in POSES)
        old_rotation = old_selected['rotation']
        assert ref['trialOldGeometryOriginVisits'] <= ref[REFERENCE_POSE_KEYS[old_rotation]]

    differing = [item for item in sorted(trial_by_id.values(), key=lambda row: row['placeOrdinal'])
                 if old_by_id[item['componentId']]['selectedSearch']['selectedKey'] !=
                    item['selectedSearch']['selectedKey']]
    divergence = trial_capture['firstSelectedFootprintDivergence']
    assert (divergence is None) is (len(differing) == 0), \
        label + ' first selected-footprint divergence missing or spurious'
    visits = trial_capture['divergenceVisits']
    if divergence is not None:
        item = differing[0]
        item_id = item['componentId']
        old, trial = old_by_id[item_id], trial_by_id[item_id]
        assert divergence['componentId'] == item_id, label + ' selected-footprint divergence is not first placement-order difference'
        assert divergence['oldPlaceOrdinal'] == old['placeOrdinal']
        assert divergence['trialPlaceOrdinal'] == trial['placeOrdinal']
        assert divergence['oldSelectedKey'] == old['selectedSearch']['selectedKey']
        assert divergence['trialSelectedKey'] == trial['selectedSearch']['selectedKey']
        source_equal = _source_geometry_equal(old, trial)
        outline_equal = _outline_equal(old_capture['outline'], trial_capture['outline'])
        boundary_equal = _boundary_equal(old_capture, trial_capture)
        same_order = old['placeOrdinal'] == trial['placeOrdinal']
        prefix_equal = old['prefix'] == trial['prefix']
        guidance_equal = old['guidance'] == trial['guidance']
        flags = {'sourceGeometryEqual': source_equal,
                 'sizingEqual': sizing_equal,
                 'outlineEqual': outline_equal,
                 'boundaryEqual': boundary_equal,
                 'samePlaceOrdinal': same_order,
                 'prefixEqual': prefix_equal,
                 'guidanceEqual': guidance_equal}
        for key, expected in flags.items():
            assert divergence[key] is expected, label + ' forged divergence context flag: ' + key
        comparable = source_equal and outline_equal and boundary_equal and same_order and prefix_equal and guidance_equal
        assert divergence['comparable'] is comparable
        expected_reasons = [code for flag, code in (
            ('sourceGeometryEqual', 'SOURCE_GEOMETRY_DIFF'),
            ('sizingEqual', 'SIZING_DIFF'),
            ('outlineEqual', 'OUTLINE_DIFF'),
            ('boundaryEqual', 'BOUNDARY_DIFF'),
            ('samePlaceOrdinal', 'ITEM_ORDER_DIFF'),
            ('prefixEqual', 'PLACED_PREFIX_DIFF'),
            ('guidanceEqual', 'GUIDANCE_DIFF')) if not flags[flag]]
        assert divergence['reasonCodes'] == expected_reasons, label + ' divergence reason codes differ from recomputed flags'
        assert divergence['oldVisitCount'] == old['selectedSearch']['visitCount']
        assert divergence['trialVisitCount'] == trial['selectedSearch']['visitCount']
        if visits is not None:
            _validate_trace_events(visits['old'], old, label + '.divergenceVisits.old')
            _validate_trace_events(visits['trial'], trial, label + '.divergenceVisits.trial')
            _check_reference_origin_visits(trial['referenceOrigin'], old['selectedSearch'],
                                           visits['trial'], label + '.referenceOrigin')
    expected_first, first_verified = _expected_first_divergence(
        old_capture, trial_capture, old_by_id, trial_by_id, divergence, visits)
    actual_first = trial_capture['firstDivergence']
    if expected_first is not None and first_verified:
        assert actual_first == expected_first, label + ' firstDivergence is not the earliest independently observed stage'
    elif actual_first is not None:
        first_verified = False
    complete = (old_capture['status'] == 'COMPLETE' and trial_capture['status'] == 'COMPLETE' and
                old_capture['lifecycle']['started'] and old_capture['lifecycle']['stopped'] and
                trial_capture['lifecycle']['started'] and trial_capture['lifecycle']['stopped'])
    local_comparable = (complete and divergence is not None and divergence['comparable'] and
                        divergence['visitArraysComplete'] and visits is not None)
    if not complete:
        local_attribution = 'INCONCLUSIVE'
    elif divergence is None:
        local_attribution = 'NO_SELECTED_FOOTPRINT_DIVERGENCE'
    else:
        local_attribution = 'COMPARABLE' if local_comparable else 'INCONCLUSIVE'
    return {'rootSeed': old_capture['rootSeed'], 'routeVersions': [15, 16],
            'captureFidelity': 'PASS' if complete else 'INCONCLUSIVE',
            'localAttribution': local_attribution,
            'firstDivergence': actual_first,
            'firstSelectedFootprintDivergence': divergence,
            'sizingEqual': sizing_equal,
            'outlineEqual': _outline_equal(old_capture['outline'], trial_capture['outline']),
            'boundaryEqual': _boundary_equal(old_capture, trial_capture),
            'firstDivergenceIndependentlyVerified': first_verified,
            'oldCaptureStatus': old_capture['status'],
            'trialCaptureStatus': trial_capture['status'],
            'limits': 'Observed visits only; no unvisited pose/origin fit, score, or counterfactual is inferred'}


def _phase_report_counts(phase):
    if phase == 'unit':
        return ((PROTOCOL_SAMPLE_PREFIX, 1), (SOURCE_PREFIX, 14),
                (POLICY_INPUT_PREFIX, 14))
    return ((ROUTE_PREFIX, 4), (CAPTURE_PREFIX, 4))


def _phase_report_order(phase):
    if phase == 'unit':
        return [PROTOCOL_SAMPLE_PREFIX] + [SOURCE_PREFIX] * 14 + [POLICY_INPUT_PREFIX] * 14
    return [ROUTE_PREFIX, ROUTE_PREFIX, CAPTURE_PREFIX, CAPTURE_PREFIX] * 2


def check_phase(text, receipt, timing, suites):
    """Validate one maintained native phase log, child receipt, timing, and suite order."""
    assert type(text) is str and type(receipt) is str and type(timing) is dict
    phase = timing.get('phase')
    assert phase in ('unit', 'routes')
    pairs = _suite_pairs(suites)
    expected_count = 3 if phase == 'unit' else 2
    assert len(pairs) == expected_count, 'Native choice phase requires frozen unit3/routes2 suites'
    manifest = json.loads(phase_suites_path(phase).read_text(encoding='utf-8'))
    assert _suite_pairs(manifest) == pairs, 'Caller suite order differs from the frozen phase manifest'

    assert set(timing) == TIMING_FIELDS, 'Native timing schema changed'
    assert timing['status'] == 'PASS' and type(timing['exitCode']) is int and timing['exitCode'] == 0
    assert type(timing['suiteCount']) is int and timing['suiteCount'] == expected_count
    elapsed = timing['elapsedSeconds']
    assert type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0.0
    assert timing['terminalCleanupIncluded'] is True
    assert timing['cleanupDuration'] == 'NOT_SEPARATELY_MEASURED'
    assert timing['scope'] == TIMING_SCOPE

    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    assert not any(FAILURE_LINE.search(line) for line in text_lines + receipt_lines), \
        'Native phase contains failure output'
    terminal = TERMINAL.format(count=expected_count)
    assert text_lines.count(terminal) == 1, 'Expected one exact native JVM suite terminal'
    assert text_lines.count(CLEANUP) == 1, 'Maintained terminal cleanup marker missing or duplicated'
    assert terminal not in receipt_lines and CLEANUP not in receipt_lines, \
        'Child receipt contains outer terminal data'
    terminal_at, cleanup_at = text_lines.index(terminal), text_lines.index(CLEANUP)
    assert terminal_at < cleanup_at
    assert all(text_lines.index(line) < terminal_at for line in text_lines
               if line.startswith(('PASS: PCB passive ', PROTOCOL_SAMPLE_PREFIX,
                                   SOURCE_PREFIX, POLICY_INPUT_PREFIX,
                                   ROUTE_PREFIX, CAPTURE_PREFIX)))

    expected_passes = ['PASS: ' + marker for _, marker in pairs]
    receipt_passes = [line for line in receipt_lines if line.startswith('PASS: PCB passive ')]
    text_passes = [line for line in text_lines if line.startswith('PASS: PCB passive ')]
    assert len(receipt_passes) == expected_count and len(text_passes) == expected_count
    for expected in expected_passes:
        assert sum(line.startswith(expected) for line in receipt_lines) == 1, \
            'Missing/duplicate child suite PASS'
        assert sum(line.startswith(expected) for line in text_lines) == 1, \
            'Missing/duplicate terminal suite PASS'
    assert all(line.startswith(expected) for line, expected in zip(receipt_passes, expected_passes)), \
        'Child receipt suite order changed'
    assert all(line.startswith(expected) for line, expected in zip(text_passes, expected_passes)), \
        'Terminal suite order changed'
    assert receipt_passes == text_passes, 'Terminal and child suite markers differ'
    assert all(text_lines.index(line) < terminal_at for line in text_passes)

    expected_counts = dict(_phase_report_counts(phase))
    for prefix, count in expected_counts.items():
        child_rows = [line for line in receipt_lines if line.startswith(prefix)]
        terminal_rows = [line for line in text_lines if line.startswith(prefix)]
        assert len(child_rows) == count, 'Child report count changed: ' + prefix.strip()
        assert child_rows == terminal_rows, 'Terminal and child report rows differ: ' + prefix.strip()
    for prefix in (PROTOCOL_SAMPLE_PREFIX, SOURCE_PREFIX, POLICY_INPUT_PREFIX,
                   ROUTE_PREFIX, CAPTURE_PREFIX):
        if prefix not in expected_counts:
            assert not any(line.startswith(prefix) for line in receipt_lines + text_lines), \
                'Report appeared in the wrong phase: ' + prefix.strip()
    assert not any(line.startswith(prefix) for line in receipt_lines + text_lines
                   for prefix in RAW_PREFIXES), 'Untouched raw-candidate report appeared in this phase'
    expected_report_order = _phase_report_order(phase)
    actual_report_order = [prefix for line in receipt_lines
                           for prefix in (PROTOCOL_SAMPLE_PREFIX, SOURCE_PREFIX,
                                          POLICY_INPUT_PREFIX, ROUTE_PREFIX, CAPTURE_PREFIX)
                           if line.startswith(prefix)]
    assert actual_report_order == expected_report_order, 'Phase report group/order changed'
    terminal_report_order = [prefix for line in text_lines
                              for prefix in (PROTOCOL_SAMPLE_PREFIX, SOURCE_PREFIX,
                                             POLICY_INPUT_PREFIX, ROUTE_PREFIX, CAPTURE_PREFIX)
                              if line.startswith(prefix)]
    assert terminal_report_order == expected_report_order, 'Terminal report group/order changed'
    for prefix, _ in expected_counts.items():
        extract(receipt, prefix)
    if phase == 'unit':
        check_protocol_sample(text)
    if phase == 'routes':
        route_rows = extract(receipt, ROUTE_PREFIX)
        capture_rows = extract(receipt, CAPTURE_PREFIX)
        assert all(type(row) is dict for row in route_rows + capture_rows)
        assert [(row.get('seed'), row.get('version')) for row in route_rows] == list(CASES), \
            'Four physical route reports are missing, duplicated, or reordered'
        assert [(row.get('rootSeed'), row.get('interpretation')) for row in capture_rows] == list(CASES), \
            'Four paired choice captures are missing, duplicated, or reordered'
        assert [row.get('mode') for row in capture_rows] == ['old', 'trial', 'old', 'trial']
        assert [row.get('referenceAvailable') for row in capture_rows] == [False, True, False, True]
    return {'status': 'PASS', 'phase': phase, 'suiteCount': expected_count,
            'suiteClasses': [name for name, _ in pairs],
            'orderedMarkers': [marker for _, marker in pairs]}


def check_sources(unit_text):
    """Check all fourteen literal old-source rows and derive their paired trial bindings."""
    assert type(unit_text) is str
    old = baselines()
    actual = extract(unit_text, SOURCE_PREFIX)
    expected = [{key: row[key] for key in SOURCE_FIELDS} for row in old]
    assert len(actual) == 14, 'All14 old-source case rows required'
    for index, (row, expected_row) in enumerate(zip(actual, expected)):
        assert type(row) is dict and tuple(row.keys()) == SOURCE_FIELDS, \
            'Source case field schema/order changed: ' + str(index)
        assert row == expected_row and all(type(row[key]) is type(value)
            for key, value in expected_row.items()), 'Literal old-source case changed: ' + expected_row['seed']
    # The previous policy reader validates exact canonical hashes and all derived trial source bindings.
    policy_rows = extract(unit_text, POLICY_INPUT_PREFIX)
    bindings = _PRIOR.inputs(policy_rows, old)
    assert set(bindings) == {row['seed'] for row in old}
    # Bind every emitted capture item back to the exact source component inventory,
    # not merely the four passive IDs.  The old reader has already checked that this
    # canonical string hashes to the accepted15 source and has the declared part/pad
    # counts; preserve its declaration order for the paired trial check.
    for row, baseline in zip(policy_rows, old):
        seed = baseline['seed']
        canonical = row['sourceCanonical15']
        parts = re.findall(r'\[([^\[\]]+)\]', canonical)
        inventory = []
        for canonical_ordinal, part in enumerate(parts):
            header = part.split('|', 1)[0].split(':')
            assert len(header) == 7 and type(header[0]) is str and type(header[3]) is str
            inventory.append({'componentId': header[0], 'packageId': header[3],
                              'canonicalOrdinal': canonical_ordinal})
        assert len(inventory) == baseline['parts']
        assert len({item['componentId'] for item in inventory}) == len(inventory)
        inventory.sort(key=lambda item: item['componentId'])
        for declaration_ordinal, item in enumerate(inventory):
            item['declarationOrdinal'] = declaration_ordinal
        bindings[seed]['sourceComponents'] = inventory
        bindings[seed]['parts'] = baseline['parts']
        bindings[seed]['pads'] = baseline['pads']
        bindings[seed]['sourceCanonical15Sha256'] = baseline['sourcePlanSha256']
        bindings[seed]['liveBoardGraphSha256'] = baseline['liveBoardGraphSha256']
        bindings[seed]['sourceIntentSha256'] = baseline['sourceIntentSha256']
        bindings[seed]['layoutSeed'] = baseline['layoutSeed']
        bindings[seed]['routingSeed'] = baseline['routingSeed']
    return bindings


def check_route_rows(route_rows):
    """Require the exact ordered four-row published physical reproduction."""
    old = route_baselines()
    assert type(route_rows) is list and len(route_rows) == len(CASES), \
        'Exactly four routed case rows required'
    checks = []
    for row, baseline, (seed, version) in zip(route_rows, old, CASES):
        assert type(row) is dict and tuple(row.keys()) == ROUTE_FIELDS, \
            'Changed routed row schema/order'
        assert row['seed'] == seed and row['version'] == version, \
            'Routed root/version missing, duplicated, or reordered'
        assert type(row['elapsedMillis']) is int and 0 < row['elapsedMillis'] <= 60000, \
            'Routed-case elapsed time must be a positive bounded integer'
        assert row['status'] == 'PASS', 'Routed generation did not pass: ' + seed
        for key in ROUTE_FIELDS:
            if key == 'elapsedMillis':
                continue
            assert type(row[key]) is type(baseline[key]) and row[key] == baseline[key], \
                'Published routed case changed: ' + seed + '/' + str(version) + '/' + key
        checks.append({'seed': seed, 'version': version, 'status': 'PASS',
                       'exactNonTimingFields': len(ROUTE_FIELDS) - 1})
    return checks


def assess_routes(route_rows, capture_rows, source_bindings):
    """Validate exact final routes and independently assess paired choice traces.

    ``source_bindings`` must be the map returned by check_sources for this packet's
    unit receipt. This function never infers a local cause from aggregate fitness or
    missing/overflowed observations.
    """
    route_checks = check_route_rows(route_rows)
    assert type(source_bindings) is dict and set(source_bindings) == \
        {row['seed'] for row in baselines()}, \
        'All fourteen source bindings from check_sources are required'
    return _assess_captures(route_rows, capture_rows, route_checks, source_bindings)


def _assess_captures(route_rows, capture_rows, route_checks, source_bindings):
    assert type(capture_rows) is list and len(capture_rows) == len(CASES), \
        'Exactly four ordered candidate-choice captures are required'
    route_by_case = {(row['seed'], row['version']): row for row in route_rows}
    capture_summaries = []
    capture_by_case = {}
    for index, ((seed, version), capture) in enumerate(zip(CASES, capture_rows)):
        assert type(capture) is dict, 'Choice capture row must be a JSON object'
        assert capture.get('rootSeed') == seed and capture.get('interpretation') == version, \
            'Choice capture root/version missing, duplicated, or reordered'
        expected_mode = 'old' if version == 15 else 'trial'
        assert capture.get('mode') == expected_mode, 'Choice capture mode does not match its route case'
        assert capture.get('referenceAvailable') is (version == 16), \
            'Choice capture reference availability does not match its paired version'
        binding = source_bindings[seed]
        route = route_by_case[(seed, version)]
        expected_source = (binding['sourceCanonical15Sha256'] if version == 15
                           else binding['sourcePlanSha25616'])
        for key, expected in (
                ('seed', seed), ('version', version), ('density', 14),
                ('parts', binding['parts']), ('pads', binding['pads']),
                ('sourcePlanSha256', expected_source),
                ('liveBoardGraphSha256', binding['liveBoardGraphSha256']),
                ('sourceIntentSha256', binding['sourceIntentSha256']),
                ('layoutSeed', binding['layoutSeed']),
                ('routingSeed', binding['routingSeed'])):
            assert route[key] == expected, \
                'Routed physical row is not bound to its accepted source inputs: ' + seed + '/' + str(version) + '/' + key
        summary = _validate_capture(capture, 'capture[' + str(index) + ']', binding)
        capture_summaries.append(summary)
        capture_by_case[(seed, version)] = capture

    roots = []
    for seed in ROOTS:
        old_capture = capture_by_case[(seed, 15)]
        trial_capture = capture_by_case[(seed, 16)]
        if old_capture['status'] == 'COMPLETE' and trial_capture['status'] == 'COMPLETE':
            root_result = _assess_capture_pair(old_capture, trial_capture, 'root' + seed)
        else:
            root_result = {
                'rootSeed': seed,
                'routeVersions': [15, 16],
                'captureFidelity': 'INCONCLUSIVE',
                'localAttribution': 'INCONCLUSIVE',
                'firstDivergence': trial_capture.get('firstDivergence'),
                'firstSelectedFootprintDivergence': None,
                'firstDivergenceIndependentlyVerified': False,
                'oldCaptureStatus': old_capture['status'],
                'trialCaptureStatus': trial_capture['status'],
                'limits': 'Incomplete frames are not paired; no missing visit, pose, origin, or cause is inferred'}
        roots.append(root_result)

    capture_fidelity = ('PASS' if all(row['status'] == 'COMPLETE' for row in capture_rows)
                        else 'INCONCLUSIVE')
    local_statuses = [row['localAttribution'] for row in roots]
    local_attribution = ('COMPARABLE' if local_statuses and
                         all(status == 'COMPARABLE' for status in local_statuses)
                         else 'INCONCLUSIVE')
    return {
        'status': 'PASS' if capture_fidelity == 'PASS' else 'INCONCLUSIVE',
        'routeFidelity': 'PASS',
        'captureFidelity': capture_fidelity,
        'localAttribution': local_attribution,
        'routeChecks': route_checks,
        'captureCases': capture_summaries,
        'roots': roots,
        'limits': 'Reader fidelity only; observed visits only, no unvisited pose/origin fit, score, counterfactual, route acceptance, or policy-quality claim'}

