"""Synthetic positive and rejection canaries for the passive-choice reader."""
import copy
import hashlib
import json
import math
import struct
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / 'assessment-canaries.json').read_text(encoding='utf-8'))
EXPECTED_NAMES = [item['id'] for group in ('positiveCases', 'readerRejections', 'gateOutcomes')
                  for item in MANIFEST[group]]
completed = []
assertions = 0


def check(value, message):
    global assertions
    assertions += 1
    if not value:
        raise AssertionError(message)


def record(case_id):
    check(case_id not in completed, 'Canary case repeated: ' + case_id)
    completed.append(case_id)


def reject(case_id, action):
    try:
        action()
    except (AssertionError, KeyError, TypeError, ValueError):
        record(case_id)
        return
    raise AssertionError('Expected reader rejection did not occur: ' + case_id)


def bits(number):
    return struct.pack('>d', float(number)).hex()


def rect(x, y, w, h):
    return {'x': x, 'y': y, 'w': w, 'h': h}


def source_fixture_text():
    spec = MANIFEST['sourceFixture']
    path = HERE.parents[3] / Path(spec['path'])
    # The path is deliberately resolved from the current repository artifact tree.
    assert path.as_posix().endswith(spec['path'].replace('docs/', ''))
    raw = path.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == spec['sha256'], \
        'Hash-bound prior unit source fixture changed'
    return raw.decode('utf-8')


def geometry_key(package, rotation, width=10, height=10, variant='SYNTHETIC_VARIANT',
                 transform='SYNTHETIC_TRANSFORM', contract='v1'):
    return '|'.join((package, variant, transform, contract, rotation, 'TOP',
                     str(width) + 'x' + str(height)))


def snapshot_key(package, rotation, x, y, width=10, height=10,
                 variant='SYNTHETIC_VARIANT', transform='SYNTHETIC_TRANSFORM'):
    return '|'.join((package, variant, transform, rotation, 'TOP',
                     str(width) + 'x' + str(height))) + '@' + str(x) + ',' + str(y)


def event(ordinal, rotation, key, x, y, score, kind='ring'):
    if kind == 'ring':
        ring, index, fallback = 0, ordinal, None
        visit_key = 'ring:0:' + str(ordinal)
    else:
        ring, index, fallback = None, None, ordinal
        visit_key = 'fallback:' + str(ordinal)
    return {'visitKey': visit_key, 'kind': kind, 'ordinal': ordinal, 'ring': ring,
            'index': index, 'fallbackOrdinal': fallback, 'requestedX': x,
            'requestedY': y, 'x': x, 'y': y, 'rotation': rotation,
            'geometryKey': key, 'legal': True, 'scored': True,
            'scoreBits': bits(score), 'incumbentUpdate': True}


def ring_event(ordinal, ring, index, rotation, key):
    row = event(ordinal, rotation, key, 100, 200, 1.0)
    row.update({'visitKey': 'ring:' + str(ring) + ':' + str(index),
                'ring': ring, 'index': index})
    return row


def pose_stats(events):
    result = {}
    for rotation in assess.POSES:
        pose_events = [row for row in events if row['rotation'] == rotation]
        ring = [row for row in pose_events if row['kind'] == 'ring']
        fallback = [row for row in pose_events if row['kind'] == 'fallback']
        scores = [row for row in pose_events if row['scored']]
        minimum = None
        for row in scores:
            value = struct.unpack('>d', bytes.fromhex(row['scoreBits']))[0]
            if minimum is None or value < minimum[0]:
                minimum = (value, row)
        first_fallback = next((row for row in fallback if row['legal']), None)
        result[rotation] = {
            'visits': len(pose_events), 'legal': sum(row['legal'] for row in pose_events),
            'scored': len(scores),
            'minimumScoreBits': minimum[1]['scoreBits'] if minimum else None,
            'minimumVisitKey': minimum[1]['visitKey'] if minimum else None,
            'incumbentUpdates': sum(row['incumbentUpdate'] for row in pose_events),
            'ringVisits': len(ring), 'ringLegal': sum(row['legal'] for row in ring),
            'ringScored': sum(row['scored'] for row in ring),
            'fallbackVisits': len(fallback), 'fallbackLegal': sum(row['legal'] for row in fallback),
            'fallbackScored': sum(row['scored'] for row in fallback),
            'firstFallbackLegalKey': first_fallback['visitKey'] if first_fallback else None}
    return result


def guidance(x, y):
    return {'hintXBits': None, 'hintYBits': None, 'connectedX': x, 'connectedY': y,
            'preferredBeforeGroupX': x, 'preferredBeforeGroupY': y,
            'preferredX': x, 'preferredY': y, 'group': None, 'direction': 0,
            'baseX': x, 'baseY': y, 'boundaryPackage': False, 'boundaryX': -1,
            'support': None, 'vacancies': [], 'vacancyPreference': None,
            'vacancyPocket': None, 'sourceEnvelope': rect(x - 20, y - 20, 50, 50)}


def item_from_source(source, declaration_ordinal, place_ordinal, policy_version,
                     divergent, root_seed, x, y, prefix, events):
    component_id, package = source['componentId'], source['packageId']
    canonical = package in assess.PASSIVE_PACKAGES
    eligible = policy_version == 1 and canonical
    source_rotation = 'DEG_180' if package == 'OPTOCOUPLER_4' else 'DEG_0'
    source_key = geometry_key(package, source_rotation)
    alternate_key = geometry_key(package, 'DEG_90') if eligible else None
    selected_event = events[-1]
    selected_key = selected_event['geometryKey']
    selected_rotation = selected_event['rotation']
    selected_fingerprint = ('fingerprint:alternate:' if selected_rotation == 'DEG_90'
                            else 'fingerprint:source:') + component_id
    return {
        'componentId': component_id, 'declarationOrdinal': declaration_ordinal,
        'domainId': 'synthetic-domain', 'regionId': 'synthetic-region',
        'anchor': 'NONE', 'declaredAnchor': 'NONE', 'packageId': package,
        'canonicalPassive': canonical, 'passivePolicyEligible': eligible,
        'sourceGeometryKey': source_key,
        'sourceGeometryFingerprint': 'fingerprint:source:' + component_id,
        'sourceRotation': source_rotation, 'sourceSide': 'TOP', 'sourceX': x,
        'sourceY': y, 'sourceWidth': 10, 'sourceHeight': 10, 'accessMargin': 20,
        'alternateGeometryKey': alternate_key,
        'alternateGeometryFingerprint': 'fingerprint:alternate:' + component_id if eligible else None,
        'alternateRotation': 'DEG_90' if eligible else None,
        'envelopeTOP0': rect(x - 20, y - 20, 50, 50),
        'envelopeTOP90': rect(x - 20, y - 20, 50, 50) if eligible else None,
        'sizingDemandArea': '100', 'sizingEnvelopeWidth': 10,
        'sizingEnvelopeHeight': 10, 'placeOrdinal': place_ordinal,
        'orderKind': 'remaining', 'evaluationDelta': 1, 'prefix': prefix,
        'guidance': guidance(x, y),
        'selectedSearch': {'selected': True, 'x': selected_event['x'],
            'y': selected_event['y'], 'rotation': selected_rotation, 'side': 'TOP',
            'geometryKey': selected_key, 'geometryFingerprint': selected_fingerprint,
            'scoreBits': selected_event['scoreBits'], 'fallbackUsed': False,
            'visitCount': len(events), 'selectedKey': selected_key + '@' +
                str(selected_event['x']) + ',' + str(selected_event['y'])},
        'fallback': None, 'poseStats': pose_stats(events), 'referenceOrigin': None,
    }


def plan_snapshot(items):
    footprints = []
    for item in sorted(items, key=lambda row: row['placeOrdinal']):
        selected = item['selectedSearch']
        pieces = selected['geometryKey'].split('|')
        footprints.append({'componentId': item['componentId'], 'packageId': item['packageId'],
            'geometryVariantKey': pieces[1], 'geometryTransformKey': pieces[2],
            'x': selected['x'], 'y': selected['y'], 'width': int(pieces[6].split('x')[0]),
            'height': int(pieces[6].split('x')[1]), 'rotation': selected['rotation'],
            'side': selected['side'],
            'selectedKey': snapshot_key(item['packageId'], selected['rotation'],
                selected['x'], selected['y'], int(pieces[6].split('x')[0]),
                int(pieces[6].split('x')[1]), pieces[1], pieces[2]),
            'geometryFingerprint': selected['geometryFingerprint']})
    return {'candidate': 1, 'demandArea': '10000', 'courtyardArea': '10000',
            'evaluations': len(items), 'outline': rect(0, 0, 100, 100),
            'footprints': footprints}


def make_capture(binding, policy_version, old_capture=None, change_outline=False):
    seed = binding['seed']
    mode = 'trial' if policy_version == 1 else 'old'
    inventory = binding['sourceComponents']
    diverging = next(row for row in inventory if row['packageId'] in assess.PASSIVE_PACKAGES)
    placement_order = [diverging['componentId']] + [row['componentId'] for row in inventory
                                                     if row['componentId'] != diverging['componentId']]
    by_id = {row['componentId']: row for row in inventory}
    items_by_id, events_by_id = {}, {}
    for place_ordinal, component_id in enumerate(placement_order):
        source = by_id[component_id]
        x, y = 100 + (place_ordinal % 5) * 30, 200 + (place_ordinal // 5) * 25
        canonical = source['packageId'] in assess.PASSIVE_PACKAGES
        eligible = policy_version == 1 and canonical
        source_rotation = 'DEG_180' if source['packageId'] == 'OPTOCOUPLER_4' else 'DEG_0'
        source_key = geometry_key(source['packageId'], source_rotation)
        if component_id == diverging['componentId']:
            events = [event(0, 'DEG_0', source_key, x, y, 2.0)]
            if policy_version == 0:
                events.append(event(1, 'DEG_0', source_key, x, y, 1.0))
            else:
                events.append(event(1, 'DEG_90', geometry_key(source['packageId'], 'DEG_90'), x, y, 1.0))
        else:
            events = [event(0, source_rotation, source_key, x, y, 1.0)]
        events_by_id[component_id] = events
        items_by_id[component_id] = item_from_source(
            source, source['declarationOrdinal'], place_ordinal, policy_version,
            component_id == diverging['componentId'], seed, x, y, [], events)
    for place_ordinal, component_id in enumerate(placement_order):
        item = items_by_id[component_id]
        item['prefix'] = [{'componentId': previous_id,
                           'selectedKey': item_from_source_snapshot_key(items_by_id[previous_id])}
                          for previous_id in placement_order[:place_ordinal]]
    items = [items_by_id[row['componentId']] for row in inventory]
    if policy_version == 1:
        assert old_capture is not None
        old_by_id = {item['componentId']: item for item in old_capture['items']}
        for item in items:
            old_selected = old_by_id[item['componentId']]['selectedSearch']
            visits = events_by_id[item['componentId']]
            same_origin = {rotation: sum(1 for visit in visits
                if visit['x'] == old_selected['x'] and visit['y'] == old_selected['y'] and
                   visit['rotation'] == rotation) for rotation in assess.POSES}
            old_geometry = sum(1 for visit in visits
                if visit['x'] == old_selected['x'] and visit['y'] == old_selected['y'] and
                   visit['geometryKey'] == old_selected['geometryKey'])
            item['referenceOrigin'] = {
                'itemFound': True, 'oldSelectedRotation': old_selected['rotation'],
                'oldSelectedX': old_selected['x'], 'oldSelectedY': old_selected['y'],
                'oldSelectedGeometryKey': old_selected['geometryKey'],
                'trialSameOriginDEG0': same_origin['DEG_0'],
                'trialSameOriginDEG90': same_origin['DEG_90'],
                'trialSameOriginDEG180': same_origin['DEG_180'],
                'trialSameOriginDEG270': same_origin['DEG_270'],
                'trialOldGeometryOriginVisits': old_geometry,
                'trialOldGeometryOriginVisited': old_geometry > 0}
    outline_rect = rect(0, 0, 101 if change_outline and policy_version == 1 else 100, 100)
    plan = plan_snapshot(items)
    plan['outline'] = copy.deepcopy(outline_rect)
    total_visits = sum(item['selectedSearch']['visitCount'] for item in items)
    first_divergence = None
    first_selected = None
    divergence_visits = None
    sizing_equal = None if policy_version == 0 else False
    if policy_version == 1:
        old_by_id = {item['componentId']: item for item in old_capture['items']}
        selected_changes = [item for item in sorted(items, key=lambda row: row['placeOrdinal'])
                            if item['selectedSearch']['selectedKey'] !=
                            old_by_id[item['componentId']]['selectedSearch']['selectedKey']]
        if selected_changes:
            item = selected_changes[0]
            old_item = old_by_id[item['componentId']]
            outline_equal = old_capture['outline']['outline'] == outline_rect
            reasons = ['SIZING_DIFF'] + ([] if outline_equal else ['OUTLINE_DIFF'])
            first_selected = {
                'componentId': item['componentId'], 'oldPlaceOrdinal': old_item['placeOrdinal'],
                'trialPlaceOrdinal': item['placeOrdinal'],
                'oldSelectedKey': old_item['selectedSearch']['selectedKey'],
                'trialSelectedKey': item['selectedSearch']['selectedKey'],
                'sourceGeometryEqual': True, 'sizingEqual': False,
                'outlineEqual': outline_equal, 'boundaryEqual': True,
                'samePlaceOrdinal': old_item['placeOrdinal'] == item['placeOrdinal'],
                'prefixEqual': old_item['prefix'] == item['prefix'],
                'guidanceEqual': old_item['guidance'] == item['guidance'],
                'comparable': outline_equal, 'reasonCodes': reasons,
                'oldVisitCount': old_item['selectedSearch']['visitCount'],
                'trialVisitCount': item['selectedSearch']['visitCount'],
                'visitArraysComplete': True, 'exportOverflow': False}
            first_divergence = {'stage': 'sizing', 'componentId': None, 'visitKey': None}
            divergence_visits = {'old': events_by_id[item['componentId']] if False else
                                 next(row for row in old_capture['items']
                                      if row['componentId'] == item['componentId'])['_syntheticEvents'],
                                 'trial': events_by_id[item['componentId']]}
    # Synthetic old event arrays are attached privately to this builder's old rows.
    # Remove that private-only field before returning the JSON-shaped capture.
    for item in items:
        item.pop('_syntheticEvents', None)
    outline = {'targetArea': '10000', 'aspectIndex': 0, 'aspectBits': bits(1.0),
        'compactFactorBits': bits(1.0), 'spacingFactorBits': bits(1.0),
        'contentWidth': 100, 'contentHeight': 100, 'outline': outline_rect,
        'outlineArea': str(outline_rect['w'] * outline_rect['h']), 'boundaryX': -1,
        'boundaryFirstDemandBits': None, 'boundaryTotalDemandBits': None,
        'boundaryFractionBits': None}
    return {
        'schema': 'pcb-passive-choice-observer-v1', 'marker': 'PCB_PASSIVE_CHOICE_CAPTURE_V1',
        'rootSeed': seed, 'layoutSeed': binding['layoutSeed'],
        'interpretation': 15 if policy_version == 0 else 16,
        'policyVersion': policy_version, 'densityVersion': 14, 'candidate': 1,
        'algorithmCandidate': 0, 'mode': mode, 'referenceAvailable': policy_version == 1,
        'expectedPartCount': binding['parts'],
        'lifecycle': {'started': True, 'stopped': True, 'planBeginCount': 1,
            'planEndCount': 1, 'planErrorCount': 0, 'planErrorType': None,
            'planBegan': True, 'planEnded': True},
        'bounds': {'maxItems': 128, 'itemCount': len(items), 'maxVisits': 60000,
            'actualVisitCount': total_visits, 'itemOverflow': False,
            'visitOverflow': False, 'maxExportVisitsPerVersion': 512,
            'exportOverflow': False},
        'status': 'COMPLETE', 'reasonCodes': [],
        'sizing': {'sourceCount': binding['parts'], 'demandArea': '10000',
            'courtyardArea': '10000', 'maxWidth': 10, 'maxHeight': 10, 'anchorWidth': 10},
        'sizingEqual': sizing_equal, 'outline': outline,
        'compaction': {'pre': plan, 'post': copy.deepcopy(plan)},
        'firstDivergence': first_divergence, 'items': items,
        'firstSelectedFootprintDivergence': first_selected,
        'divergenceVisits': divergence_visits}


def item_from_source_snapshot_key(item):
    selected = item['selectedSearch']
    pieces = selected['geometryKey'].split('|')
    geometry = '|'.join(pieces[:3] + pieces[4:])
    return geometry + '@' + str(selected['x']) + ',' + str(selected['y'])


def make_old_capture(binding):
    capture = make_capture(binding, 0)
    for item in capture['items']:
        item['_syntheticEvents'] = []
    # Keep the exact private event list outside serialized rows for the paired trial.
    source = next(row for row in binding['sourceComponents']
                  if row['packageId'] in assess.PASSIVE_PACKAGES)
    item = next(row for row in capture['items'] if row['componentId'] == source['componentId'])
    x, y = item['selectedSearch']['x'], item['selectedSearch']['y']
    key = item['sourceGeometryKey']
    item['_syntheticEvents'] = [event(0, 'DEG_0', key, x, y, 2.0),
                                event(1, 'DEG_0', key, x, y, 1.0)]
    return capture


def make_paired_captures(binding, change_outline=False):
    old_capture = make_old_capture(binding)
    # Private event vectors are used only by this fixture builder.
    old_events = next(item['_syntheticEvents'] for item in old_capture['items']
                      if item['_syntheticEvents'])
    old_item = next(item for item in old_capture['items'] if item['_syntheticEvents'])
    old_item['selectedSearch']['visitCount'] = 2
    old_item['poseStats'] = pose_stats(old_events)
    old_capture['compaction']['pre'] = plan_snapshot(old_capture['items'])
    old_capture['compaction']['post'] = copy.deepcopy(old_capture['compaction']['pre'])
    # The serialized old capture contains summary only; the builder sidecar is never emitted.
    old_for_trial = copy.deepcopy(old_capture)
    old_for_trial['_privateEvents'] = old_events
    for item in old_capture['items']:
        item.pop('_syntheticEvents', None)
    trial = make_capture(binding, 1, old_for_trial, change_outline)
    # Replace the intentionally selected event trace with the paired actual vectors.
    if trial['divergenceVisits'] is not None:
        trial['divergenceVisits']['old'] = old_events
    return old_capture, trial


def protocol_sample():
    binding = {'seed': '42', 'layoutSeed': '9001', 'parts': 1,
        'sourceComponents': [{'componentId': 'R1', 'packageId': 'AXIAL_RESISTOR',
                             'canonicalOrdinal': 0, 'declarationOrdinal': 0}]}
    source = binding['sourceComponents'][0]
    key = geometry_key('AXIAL_RESISTOR', 'DEG_0')
    events = [event(0, 'DEG_0', key, 100, 200, 2.0),
              event(1, 'DEG_0', key, 100, 200, 1.0),
              event(2, 'DEG_0', key, 100, 200, 1.0)]
    events[2]['incumbentUpdate'] = False
    item = item_from_source(source, 0, 0, 0, False, '42', 100, 200, [], events)
    item['domainId'], item['regionId'] = 'domain', 'region'
    outline_rect = rect(0, 0, 100, 100)
    plan = plan_snapshot([item])
    plan['outline'] = copy.deepcopy(outline_rect)
    outline = {'targetArea': '10000', 'aspectIndex': 0, 'aspectBits': bits(1),
        'compactFactorBits': bits(1), 'spacingFactorBits': bits(1),
        'contentWidth': 100, 'contentHeight': 100, 'outline': outline_rect,
        'outlineArea': '10000', 'boundaryX': -1, 'boundaryFirstDemandBits': None,
        'boundaryTotalDemandBits': None, 'boundaryFractionBits': None}
    return {
        'schema': 'pcb-passive-choice-observer-v1', 'marker': 'PCB_PASSIVE_CHOICE_CAPTURE_V1',
        'rootSeed': '42', 'layoutSeed': '9001', 'interpretation': 15, 'policyVersion': 0,
        'densityVersion': 14, 'candidate': 1, 'algorithmCandidate': 0, 'mode': 'old',
        'referenceAvailable': False, 'expectedPartCount': 1,
        'lifecycle': {'started': True, 'stopped': True, 'planBeginCount': 1,
            'planEndCount': 1, 'planErrorCount': 0, 'planErrorType': None,
            'planBegan': True, 'planEnded': True},
        'bounds': {'maxItems': 128, 'itemCount': 1, 'maxVisits': 60000,
            'actualVisitCount': 3, 'itemOverflow': False, 'visitOverflow': False,
            'maxExportVisitsPerVersion': 512, 'exportOverflow': False},
        'status': 'COMPLETE', 'reasonCodes': [],
        'sizing': {'sourceCount': 1, 'demandArea': '100', 'courtyardArea': '100',
            'maxWidth': 10, 'maxHeight': 10, 'anchorWidth': 10},
        'sizingEqual': None, 'outline': outline,
        'compaction': {'pre': plan, 'post': copy.deepcopy(plan)},
        'firstDivergence': None, 'items': [item],
        'firstSelectedFootprintDivergence': None, 'divergenceVisits': None}


def route_rows():
    return copy.deepcopy(assess.route_baselines())


def all_capture_rows(bindings, outline_change=True):
    rows = []
    pairs = {}
    for seed in assess.ROOTS:
        pair = make_paired_captures(bindings[seed], change_outline=(outline_change and seed == '42'))
        pairs[seed] = pair
        rows.extend(pair)
    return rows, pairs


def phase_fixture(phase, source_text, sample=None, route_data=None, capture_data=None):
    suites = json.loads(assess.phase_suites_path(phase).read_text(encoding='utf-8'))
    lines = []
    if phase == 'unit':
        lines.append(assess.PROTOCOL_SAMPLE_PREFIX + json.dumps(sample, separators=(',', ':')))
        for prefix in (assess.SOURCE_PREFIX, assess.POLICY_INPUT_PREFIX):
            lines.extend(line for line in source_text.splitlines() if line.startswith(prefix))
    else:
        for root_index in range(2):
            base = root_index * 2
            lines.extend(assess.ROUTE_PREFIX + json.dumps(row, separators=(',', ':'))
                         for row in route_data[base:base + 2])
            lines.extend(assess.CAPTURE_PREFIX + json.dumps(row, separators=(',', ':'))
                         for row in capture_data[base:base + 2])
    pass_lines = ['PASS: ' + marker for _, marker in suites]
    receipt = '\n'.join(lines + pass_lines) + '\n'
    count = 3 if phase == 'unit' else 2
    text = receipt + assess.TERMINAL.format(count=count) + '\n' + assess.CLEANUP + '\n'
    timing = {'status': 'PASS', 'exitCode': 0, 'elapsedSeconds': 1.25,
        'suiteCount': count, 'phase': phase, 'scope': assess.TIMING_SCOPE,
        'terminalCleanupIncluded': True, 'cleanupDuration': 'NOT_SEPARATELY_MEASURED'}
    return text, receipt, timing, suites


def main():
    source_text = source_fixture_text()
    bindings = assess.check_sources(source_text)
    check(len(bindings) == 14 and bindings['42']['parts'] == 54 and
          bindings['3']['parts'] == 49, 'All fourteen hash-bound source bindings')
    record('P01_all14_hash_bound_source_bindings')

    sample = protocol_sample()
    sample_text = assess.PROTOCOL_SAMPLE_PREFIX + json.dumps(sample, separators=(',', ':')) + '\n'
    sample_result = assess.check_protocol_sample(sample_text)
    check(sample_result == {'status': 'PASS', 'sampleCount': 1,
          'schema': 'pcb-passive-choice-observer-v1',
          'marker': 'PCB_PASSIVE_CHOICE_CAPTURE_V1', 'syntheticOnly': True},
          'Synthetic protocol sample is schema-only')
    record('P02_protocol_sample_schema_only')

    physical_rows = route_rows()
    captures, pairs = all_capture_rows(bindings)
    assessment = assess.assess_routes(physical_rows, captures, bindings)
    check(assessment['status'] == 'PASS' and assessment['routeFidelity'] == 'PASS' and
          assessment['captureFidelity'] == 'PASS' and
          assessment['roots'][0]['localAttribution'] == 'INCONCLUSIVE' and
          assessment['roots'][1]['localAttribution'] == 'COMPARABLE',
          'Four source-bound synthetic captures assess independently from local context')
    record('P03_full_paired_actual_visit_assessment')

    text, receipt, timing, suites = phase_fixture('unit', source_text, sample=sample)
    unit = assess.check_phase(text, receipt, timing, suites)
    check(unit['status'] == 'PASS' and unit['suiteCount'] == 3,
          'Synthetic unit phase validates exact reports and suite order')
    record('P04_unit_phase_receipt_and_report_order')
    text, receipt, timing, suites = phase_fixture('routes', source_text,
        route_data=physical_rows, capture_data=captures)
    routes_phase = assess.check_phase(text, receipt, timing, suites)
    check(routes_phase['status'] == 'PASS' and routes_phase['suiteCount'] == 2,
          'Synthetic route phase validates exact reports and suite order')
    record('P05_route_phase_receipt_and_report_order')

    provider_key = geometry_key('OPTOCOUPLER_4', 'DEG_180')
    anchor_last_slot = ring_event(9, 1, 4, 'DEG_180', provider_key)
    remaining_last_slot = ring_event(11, 1, 5, 'DEG_180', provider_key)
    provider_source = {'componentId': 'UFB', 'packageId': 'OPTOCOUPLER_4'}
    anchor_item = item_from_source(provider_source, 0, 0, 0, False, '42',
                                   100, 200, [], [anchor_last_slot])
    anchor_item['orderKind'] = 'anchor'
    remaining_item = item_from_source(provider_source, 0, 0, 0, False, '42',
                                      100, 200, [], [remaining_last_slot])
    assess._validate_trace_events([anchor_last_slot], anchor_item, 'anchorLastSlot')
    assess._validate_trace_events([remaining_last_slot], remaining_item, 'remainingLastSlot')
    record('P06_anchor5_remaining6_provider_pose')

    # Duplicate JSON member names must fail before schema validation.
    reject('R01_duplicate_json_key', lambda: assess.extract(
        assess.PROTOCOL_SAMPLE_PREFIX + '{"schema":"x","schema":"y"}\n',
        assess.PROTOCOL_SAMPLE_PREFIX))
    bad_source_text = source_text.replace('PCB_PASSIVE_POSE_SOURCE_CASE {',
        'PCB_PASSIVE_POSE_SOURCE_CASE {"seed":"999",', 1)
    reject('R02_changed_source_fixture_binding', lambda: assess.check_sources(bad_source_text))
    text, receipt, timing, suites = phase_fixture('unit', source_text, sample=sample)
    bad_text = text.replace(assess.PROTOCOL_SAMPLE_PREFIX, 'TEMP_PREFIX ', 1)
    reject('R03_reordered_terminal_report_groups', lambda: assess.check_phase(
        bad_text, receipt, timing, suites))
    bad_routes = copy.deepcopy(physical_rows)
    bad_routes[0], bad_routes[1] = bad_routes[1], bad_routes[0]
    reject('R04_reordered_physical_route_rows', lambda: assess.assess_routes(
        bad_routes, captures, bindings))
    bad_captures = copy.deepcopy(captures)
    bad_captures[0], bad_captures[1] = bad_captures[1], bad_captures[0]
    reject('R05_reordered_choice_captures', lambda: assess.assess_routes(
        physical_rows, bad_captures, bindings))
    bad = copy.deepcopy(pairs['42'][0])
    bad['candidate'] = 2
    reject('R06_candidate_identity_mismatch', lambda: assess._validate_capture(
        bad, 'badCandidate', bindings['42']))
    bad = copy.deepcopy(pairs['42'][0])
    provider = next(item for item in bad['items'] if item['packageId'] == 'OPTOCOUPLER_4')
    provider['sourceRotation'] = 'DEG_0'
    reject('R07_provider_pose_not_preserved', lambda: assess._validate_capture(
        bad, 'badProviderPose', bindings['42']))
    bad_events = copy.deepcopy(pairs['42'][1]['divergenceVisits']['trial'])
    bad_events[1]['rotation'] = 'DEG_0'
    trial_divergence_item = next(item for item in pairs['42'][1]['items']
        if item['componentId'] == pairs['42'][1]['firstSelectedFootprintDivergence']['componentId'])
    reject('R08_eligible_pose_parity_mismatch', lambda: assess._validate_trace_events(
        bad_events, trial_divergence_item, 'badSchedule'))
    bad_events = copy.deepcopy(pairs['42'][1]['divergenceVisits']['trial'])
    bad_events[1]['scoreBits'] = '7ff0000000000000'
    reject('R09_nonfinite_actual_score', lambda: assess._validate_trace_events(
        bad_events, trial_divergence_item, 'badScore'))
    bad = copy.deepcopy(captures)
    trial_capture = bad[1]
    diff_id = trial_capture['firstSelectedFootprintDivergence']['componentId']
    item = next(row for row in trial_capture['items'] if row['componentId'] == diff_id)
    item['referenceOrigin']['trialSameOriginDEG90'] = 0
    reject('R10_fabricated_reference_origin_count', lambda: assess.assess_routes(
        physical_rows, bad, bindings))
    bad = copy.deepcopy(captures)
    trial_capture = bad[1]
    first = min(trial_capture['items'], key=lambda row: row['placeOrdinal'])
    wrong_key = first['selectedSearch']['selectedKey']
    next_item = next(row for row in trial_capture['items'] if row['placeOrdinal'] == 1)
    next_item['prefix'][0]['selectedKey'] = wrong_key
    reject('R11_prefix_key_contract_version_leak', lambda: assess.assess_routes(
        physical_rows, bad, bindings))
    bad_routes = copy.deepcopy(physical_rows)
    bad_routes[0]['vias'] += 1
    reject('R12_changed_physical_route_metric', lambda: assess.assess_routes(
        bad_routes, captures, bindings))
    reject('R13_missing_capture_row', lambda: assess.assess_routes(
        physical_rows, captures[:-1], bindings))
    bad = copy.deepcopy(pairs['42'][1])
    bad['bounds']['exportOverflow'] = True
    reject('R14_complete_capture_with_export_overflow', lambda: assess._validate_capture(
        bad, 'badExportOverflow', bindings['42']))
    reject('R15_missing_protocol_sample', lambda: assess.check_protocol_sample(''))
    bad_anchor_index = ring_event(5, 0, 5, 'DEG_180', provider_key)
    bad_anchor_item = item_from_source(provider_source, 0, 0, 0, False, '42',
                                       100, 200, [], [bad_anchor_index])
    bad_anchor_item['orderKind'] = 'anchor'
    reject('R16_anchor_sixth_ring_slot', lambda: assess._validate_trace_events(
        [bad_anchor_index], bad_anchor_item, 'badAnchorIndex'))
    bad_remaining_ordinal = ring_event(5, 1, 0, 'DEG_180', provider_key)
    bad_remaining_item = item_from_source(provider_source, 0, 0, 0, False, '42',
                                          100, 200, [], [bad_remaining_ordinal])
    reject('R17_remaining_anchor_stride_ordinal', lambda: assess._validate_trace_events(
        [bad_remaining_ordinal], bad_remaining_item, 'badRemainingOrdinal'))

    incomplete = copy.deepcopy(captures)
    incomplete[3]['status'] = 'INCONCLUSIVE'
    incomplete[3]['lifecycle']['stopped'] = False
    incomplete[3]['reasonCodes'] = ['CAPTURE_STOP_MISSING']
    outcome = assess.assess_routes(physical_rows, incomplete, bindings)
    check(outcome['routeFidelity'] == 'PASS' and outcome['captureFidelity'] == 'INCONCLUSIVE' and
          outcome['status'] == 'INCONCLUSIVE' and outcome['roots'][1]['localAttribution'] == 'INCONCLUSIVE',
          'Incomplete capture cannot borrow route fidelity or causal claims')
    record('G01_incomplete_capture_keeps_route_fidelity_separate')
    root42 = next(row for row in assessment['roots'] if row['rootSeed'] == '42')
    check(root42['captureFidelity'] == 'PASS' and root42['localAttribution'] == 'INCONCLUSIVE' and
          root42['outlineEqual'] is False,
          'A complete trace with earlier context mismatch blocks local attribution')
    record('G02_context_mismatch_blocks_local_attribution')

    check(completed == EXPECTED_NAMES, 'Executed canary set/order differs from manifest')
    positive_count = len(MANIFEST['positiveCases'])
    rejection_count = len(MANIFEST['readerRejections'])
    gate_count = len(MANIFEST['gateOutcomes'])
    result = {
        'schemaVersion': 1, 'status': 'PASS',
        'scope': 'Synthetic reader-only canaries; no new native choice captures or physical validation consumed.',
        'nativeChoiceOutputsConsumed': False, 'assertions': assertions,
        'caseCount': len(completed), 'positiveCaseCount': positive_count,
        'readerRejectionCount': rejection_count, 'gateOutcomeCount': gate_count,
        'caseIds': {'positive': [item['id'] for item in MANIFEST['positiveCases']],
            'readerRejections': [item['id'] for item in MANIFEST['readerRejections']],
            'gateOutcomes': [item['id'] for item in MANIFEST['gateOutcomes']]},
        'shapeCounts': {'sourceBindings': 14, 'routeRows': 4, 'captureRows': 4,
            'captureItemsRoot42': 54, 'captureItemsRoot3': 49,
            'unitSuites': 3, 'routeSuites': 2},
        'sourceFixtureSha256': MANIFEST['sourceFixture']['sha256'],
        'runner': 'Private Python pointer with -X utf8 -B; executable path not recorded.',
        'sha256': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
            for name in ('assess.py', 'check_assessment.py', 'assessment-canaries.json',
                         'reader-notes.md', 'route-baselines.json', 'unit-suites.json',
                         'route-suites.json')}}
    (HERE / 'assessment-canary-results.json').write_text(
        json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('PASS: PCB passive pose choice assessment canaries assertions=%d cases=%d positive=%d readerRejections=%d gateOutcomes=%d' %
          (assertions, len(completed), positive_count, rejection_count, gate_count))
    print('Synthetic reader-only checks; no new native choice outputs or physical claims consumed.')


if __name__ == '__main__':
    assert len(EXPECTED_NAMES) == len(set(EXPECTED_NAMES)), 'Manifest case IDs must be unique'
    main()
