"""Independent census/transport checks; no routed-layout qualification."""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path
from read_diagnostic import strict_json, need, integer

PREFIX = 'RB100_LAYOUT_ROUTING_DIAGNOSTIC '
SUMMARY = 'PASS: RB100 layout routing diagnostic contracts '
SCOPE = 'OBSERVATIONAL_FIXED_ROUTING_ONLY'
VARIANTS = ['SHARED_HYSTERETIC_LINEAR', 'SEPARATE_DIRECT_SWITCHING']
IDENTITY = {'seed': '9007199254740993', 'placementSeed': '-6587958367974952701',
            'routingSeed': '8445301674744508279', 'candidate': 79, 'layoutVersion': 21,
            'dependencyEpoch': 'tsj-generation-dependencies-v22', 'planVersion': 1,
            'modelVersion': 4, 'physicalIntentVersion': 2}
LIMITS = {'orderings': 5, 'ripUpPasses': 2, 'victimsPerPass': 2, 'reroutedNets': 6,
          'expansions': 1000000, 'gridCells': 700000}
PROFILE = {'physicalPolicyId': 'THT_SINGLE_FACE', 'physicalPolicyVersion': 1,
           'placementDensityVersion': 1, 'routingLayer': 'BOTTOM',
           'traceWidthProfile': 'UNIFORM_TRACE@1', 'placementPackingVersion': 4,
           'requiresTwoLayerRouting': False}


def identity(row):
    for key, expected in IDENTITY.items():
        need(type(row.get(key)) is type(expected) and row[key] == expected,
             'current exact identity differs: ' + key)
    need(row.get('scope') == SCOPE, 'observation scope differs')
    for key, expected in PROFILE.items():
        need(type(row.get(key)) is type(expected) and row[key] == expected,
             'explicit placement input differs: ' + key)
    need(integer(row.get('protocolVersion'), 2, 2) and type(row.get('runId')) is str and
         re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', row['runId']) is not None,
         'missing current invocation identity')


def java_hash(value):
    data = value.encode('utf-16-be')
    result = 0
    for index in range(0, len(data), 2):
        result = (31 * result + int.from_bytes(data[index:index + 2], 'big')) & 0xffffffff
    return format(result, 'x'), len(data) // 2


def validate_decisions(row, net_count):
    stats = row['routingStats']
    value = stats['decisions']
    digest, length = java_hash(value)
    need(integer(stats.get('decisionsLength'), 1, 65536) and stats['decisionsLength'] == length and
         stats.get('decisionsJavaHash') == digest, 'decision completeness metadata differs')
    need(value.endswith(';'), 'incomplete terminal decision record')
    records = value[:-1].split(';')
    passes = 0
    rip_count = victim_count = 0
    active_nets = set()
    first_nets = None
    for record in records:
        order = re.fullmatch(r'order\[(\d+)\]=(.*)tree=(\d)', record)
        if order is not None:
            need(int(order[1]) == passes and int(order[3]) == passes % 5, 'ordering census/tree differs')
            payload = order[2]
            nets = []
            while payload:
                field = re.match(r'(\d+):', payload)
                need(field is not None, 'invalid length-framed net order')
                count = int(field[1])
                start = field.end()
                net = payload[start:start + count]
                need(count > 0 and len(net) == count and len(payload) > start + count and
                     payload[start + count] == ',', 'truncated net order field')
                nets.append(net)
                payload = payload[start + count + 1:]
            active_nets = set(nets)
            need(len(nets) == len(active_nets) == net_count, 'incomplete/duplicate ordered net census')
            need(first_nets is None or active_nets == first_nets, 'orderings changed net membership')
            first_nets = active_nets
            passes += 1
            continue
        need(passes > 0, 'route decision before an ordering')
        rip = re.fullmatch(r'rip\[([^\[\]]+)\]=\[([^\[\]]+)\]', record)
        if rip is not None:
            victims = rip[2].split(', ')
            need(rip[1] in active_nets and 1 <= len(victims) <= 2 and
                 len(set(victims)) == len(victims) and set(victims) <= active_nets,
                 'invalid rip decision membership')
            rip_count += 1
            victim_count += len(victims)
            continue
        recovered = re.fullmatch(r'recovered\[([^\[\]]+)\]', record)
        if recovered is not None:
            need(recovered[1] in active_nets, 'unknown recovered net')
            continue
        rejection = re.fullmatch(r'reject\[([A-Z_]+):([^\[\]]+)\]', record)
        need(rejection is not None and rejection[1] in {'QUALITY', 'NO_PATH', 'GRID_LIMIT',
             'SEARCH_LIMIT', 'RECOVERY_LIMIT', 'QUALITY_LIMIT'} and
             (rejection[1] == 'QUALITY' or rejection[2] in active_nets or
              rejection[1] != 'SEARCH_LIMIT' and rejection[2] == 'null'),
             'incomplete/unknown routing decision')
    need(passes == stats['orderingPasses'] and rip_count == stats['ripUpPasses'] and
         victim_count == stats['rippedNets'], 'decision/counter census differs')
    if row['routingStatus'] == 'REJECTED':
        expected = ('reject[QUALITY:' if row['routerReason'] == 'QUALITY_LIMIT' else
                    'reject[' + row['routerReason'] + ':' +
                    ('null' if row['blockedNet'] is None else row['blockedNet']) + ']')
        need(records[-1].startswith(expected) if row['routerReason'] == 'QUALITY_LIMIT' else
             records[-1] == expected, 'terminal rejection decision differs')


def validate(cases, summary):
    need(type(summary) is dict and summary.get('status') == 'PASS' and
         integer(summary.get('cases'), 2, 2) and integer(summary.get('authoredVariants'), 2, 2),
         'exact two-case terminal summary required')
    identity(summary)
    need(type(cases) is list and len(cases) == 2, 'exact two-case census required')
    for index, row in enumerate(cases):
        need(type(row) is dict and row.get('status') == 'PASS' and row.get('variant') == VARIANTS[index],
             'row status/order differs')
        identity(row)
        need(row['runId'] == summary['runId'], 'mixed routing invocations')
        need(row.get('cleanupStatus') == row.get('boardIdentityStatus') == 'PASS' and
             row.get('failureType') == row.get('cleanupFailureType') == 'none' and
             row.get('failure') == row.get('cleanupFailure') == '', 'errors or cleanup differ')
        for key, minimum, maximum in [('operationMs', 0, 90000), ('cleanupMs', 0, 120000),
                                       ('maxUnitMs', 0, 5000), ('workUnits', 1, 640)]:
            need(integer(row.get(key), minimum, maximum), 'invalid typed budget: ' + key)
        need(integer(row.get('routeAttempt'), IDENTITY['candidate'], IDENTITY['candidate']) and
             integer(row.get('treeVariant'), 0, 0) and
             row.get('canonical') is True and type(row.get('defaultLimits')) is dict and
             row['defaultLimits'] == LIMITS and all(type(v) is int for v in row['defaultLimits'].values()),
             'selected routing arguments/default limits differ')
        outline = row.get('outline')
        need(type(outline) is dict and all(integer(outline.get(key), -1000000, 1000000)
             for key in ['x', 'y']) and all(integer(outline.get(key), 1, 1000000)
             for key in ['width', 'height']), 'invalid measured outline')
        need(type(row.get('placementFingerprint')) is str and row['placementFingerprint'] != '' and
             len(row['placementFingerprint']) <= 1024 * 1024, 'missing bounded placement fingerprint')
        connectors = row.get('selectedConnectorPlacements')
        need(type(connectors) is list and len(connectors) == 6 and
             [c.get('componentId') for c in connectors if type(c) is dict] ==
             ['JAC', 'JLOAD', 'JSA', 'JSB', 'JSC', 'JSD'], 'exact connector census/order differs')
        for connector in connectors:
            need(all(type(connector.get(key)) is str and connector[key] != '' for key in
                     ['domainId', 'declaredAnchor', 'packageId', 'geometryVariantKey',
                      'geometryTransformKey', 'rotation', 'side']) and
                 all(integer(connector.get(key), -1000000, 1000000) for key in ['x', 'y']),
                 'invalid selected connector facts')
            primary = connector['componentId'] == 'JAC'
            need(connector['domainId'] == ('PRIMARY' if primary else 'SECONDARY') and
                 connector['declaredAnchor'] == ('LEFT' if primary else 'RIGHT'),
                 'literal connector family intent differs')
        stats = row.get('routingStats')
        need(type(stats) is dict, 'missing immutable routing statistics')
        maximum_attempts = (59 if index == 0 else 60) * 5 + 6
        for key, minimum, maximum in [('routeAttempts', 0, maximum_attempts),
                ('branchSearches', 0, 1000000), ('orderingPasses', 1, 5),
                ('ripUpPasses', 0, 2), ('rippedNets', 0, 4), ('reroutedNets', 0, 6),
                ('expansions', 0, 1000000), ('candidateFailures', 0, 10),
                ('rawSegments', 0, 1000000), ('congestionRejections', 0, 2147483647)]:
            need(integer(stats.get(key), minimum, maximum), 'invalid routing statistic: ' + key)
        need(type(stats.get('decisions')) is str and 0 < len(stats['decisions']) <= 65536,
             'missing bounded untruncated route decisions')
        need(type(row.get('tracesPublished')) is bool and integer(row.get('traceCount'), 0, 1000000),
             'invalid trace publication facts')
        if row.get('routingStatus') == 'REJECTED':
            need(row.get('routerReason') in {'NO_PATH', 'GRID_LIMIT', 'SEARCH_LIMIT',
                 'RECOVERY_LIMIT', 'QUALITY_LIMIT'} and stats.get('outcome') == 'EXHAUSTED' and
                 row.get('routerExceptionType') == 'PcbNetRouter.Rejected' and
                 type(row.get('routerMessage')) is str and row['routerMessage'] != '' and
                 stats.get('rejectionReason') == row['routerReason'] and
                 row['tracesPublished'] is False and row['traceCount'] == 0 and
                 (row.get('blockedNet') is None or
                  type(row.get('blockedNet')) is str and row['blockedNet'] != ''),
                 'typed rejection/publication facts differ')
            if row['routerReason'] == 'SEARCH_LIMIT':
                need(type(row['blockedNet']) is str and row['blockedNet'] != '' and
                     stats['routeAttempts'] > 0, 'search exhaustion lacks actual blocked net/work')
        else:
            need(row.get('routingStatus') == 'SUCCESS' and stats.get('outcome') == 'SUCCESS' and
                 row['tracesPublished'] is True and row['traceCount'] > 0 and
                 row.get('routerExceptionType') == 'none' and row.get('routerReason') is None and
                 row.get('routerMessage') == '' and row.get('blockedNet') is None and
                 stats.get('rejectionReason') is None,
                 'route success facts differ')
        validate_decisions(row, 59 if index == 0 else 60)


def extract(lines):
    markers = [(kind, strict_json(line[len(prefix):])) for line in lines
               for kind, prefix in [('case', PREFIX), ('summary', SUMMARY)] if line.startswith(prefix)]
    need([kind for kind, _ in markers] == ['case', 'case', 'summary'],
         'exact two rows followed by one terminal summary required')
    cases, summary = [value for _, value in markers[:2]], markers[2][1]
    validate(cases, summary)
    return cases, summary


def read_receipt(path):
    raw = path.read_bytes()
    need(len(raw) <= 4 * 1024 * 1024, 'receipt exceeds transport bound')
    cases, summary = extract(raw.decode('utf-8-sig').splitlines())
    return cases, summary, hashlib.sha256(raw).hexdigest()


def negative_canaries(cases, summary):
    mutations = [lambda rows: rows.pop(), lambda rows: rows.__setitem__(1, copy.deepcopy(rows[0])),
        lambda rows: rows[0].__setitem__('operationMs', True),
        lambda rows: rows[0].__setitem__('cleanupStatus', 'FAIL'),
        lambda rows: rows[0].__setitem__('physicalIntentVersion', 1),
        lambda rows: rows[0].__setitem__('placementSeed', 9007199254740993),
        lambda rows: rows[0].__setitem__('canonical', 1),
        lambda rows: rows[0]['routingStats'].__setitem__('expansions', 1000001),
        lambda rows: rows[0]['routingStats'].__setitem__('orderingPasses', True),
        lambda rows: rows[0].__setitem__('boardIdentityStatus', 'FAIL'),
        lambda rows: rows[0]['selectedConnectorPlacements'].pop(),
        lambda rows: rows[0]['routingStats'].__setitem__('decisions', ''),
        lambda rows: rows[0]['routingStats'].__setitem__('decisions', rows[0]['routingStats']['decisions'][:-2]),
        lambda rows: rows[0].__setitem__('runId', '00000000-0000-0000-0000-000000000000'),
        lambda rows: rows[0].__setitem__('placementDensityVersion', 99),
        lambda rows: rows[0].__setitem__('requiresTwoLayerRouting', 1),
        lambda rows: rows[0].__setitem__('physicalPolicyId', 'MEDIUM_BOARD'),
        lambda rows: rows[0].__setitem__('routeAttempt', 1)]
    count = 0
    for mutate in mutations:
        rows = copy.deepcopy(cases)
        mutate(rows)
        try:
            validate(rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('malformed routing fixture was accepted')
    for value in ['{"status":"PASS","status":"FAIL"}', '{"x":NaN}']:
        try:
            strict_json(value)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid JSON fixture accepted')
    valid_lines = [PREFIX + json.dumps(row) for row in cases] + [SUMMARY + json.dumps(summary)]
    for lines in [[valid_lines[-1]] + valid_lines[:-1], valid_lines + [valid_lines[-1]]]:
        try:
            extract(lines)
        except ValueError:
            count += 1
        else:
            raise ValueError('stale/duplicate terminal marker was accepted')
    # Synthetic transport shapes keep these checks meaningful even if a later
    # measured routing observation succeeds. They grant no simulator evidence.
    search_rows = copy.deepcopy(cases)
    row = search_rows[0]
    row.update(routingStatus='REJECTED', routerReason='SEARCH_LIMIT', blockedNet='C_COIL_LOW',
               tracesPublished=False, traceCount=0, routerExceptionType='PcbNetRouter.Rejected',
               routerMessage='TOTAL_SEARCH_WORK_BUDGET')
    row['routingStats'].update(outcome='EXHAUSTED', rejectionReason='SEARCH_LIMIT',
                              expansions=1000000, candidateFailures=1)
    row['routingStats']['routeAttempts'] = max(1, row['routingStats']['routeAttempts'])
    records = row['routingStats']['decisions'][:-1].split(';')
    if records[-1].startswith('reject['):
        records.pop()
    text = ';'.join(records + ['reject[SEARCH_LIMIT:C_COIL_LOW]']) + ';'
    row['routingStats']['decisions'] = text
    row['routingStats']['decisionsJavaHash'], row['routingStats']['decisionsLength'] = java_hash(text)
    validate(search_rows, summary)
    for mutate in [lambda rows: rows[0].__setitem__('blockedNet', None),
                   lambda rows: rows[0].__setitem__('blockedNet', ''),
                   lambda rows: rows[0]['routingStats'].__setitem__('routeAttempts', 0)]:
        rows = copy.deepcopy(search_rows)
        mutate(rows)
        try:
            validate(rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid SEARCH_LIMIT shape accepted')
    # A nullable blocked net is valid for the actual router's QUALITY_LIMIT shape.
    rows = copy.deepcopy(search_rows)
    rows[0]['routerReason'] = rows[0]['routingStats']['rejectionReason'] = 'QUALITY_LIMIT'
    rows[0]['blockedNet'] = None
    text = rows[0]['routingStats']['decisions']
    text = text[:text.rfind('reject[')] + 'reject[QUALITY:FIXTURE];'
    rows[0]['routingStats']['decisions'] = text
    rows[0]['routingStats']['decisionsJavaHash'], rows[0]['routingStats']['decisionsLength'] = java_hash(text)
    validate(rows, summary)
    rows[0]['routerReason'] = rows[0]['routingStats']['rejectionReason'] = 'GRID_LIMIT'
    text = text[:text.rfind('reject[')] + 'reject[GRID_LIMIT:null];'
    rows[0]['routingStats']['decisions'] = text
    rows[0]['routingStats']['decisionsJavaHash'], rows[0]['routingStats']['decisionsLength'] = java_hash(text)
    validate(rows, summary)
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--rejected-receipt', action='append', type=Path, default=[])
    parser.add_argument('--candidate', type=int, choices=[0, 79], default=79)
    parser.add_argument('--placement-input', choices=['PI2_DENSITY1', 'PI3_DENSITY2',
                        'PI4_DENSITY2_REGIONS'], default='PI2_DENSITY1')
    args = parser.parse_args()
    need(args.candidate == 0 or args.placement_input == 'PI2_DENSITY1',
         'only the frozen first candidate may use the compact comparison')
    IDENTITY['candidate'] = args.candidate
    if args.placement_input != 'PI2_DENSITY1':
        IDENTITY['physicalIntentVersion'] = 3 if args.placement_input == 'PI3_DENSITY2' else 4
        PROFILE['placementDensityVersion'] = 2
    need(not args.output.exists(), 'refusing existing output')
    cases, summary, digest = read_receipt(args.receipt)
    checked = negative_canaries(cases, summary)
    for rejected in args.rejected_receipt:
        try:
            read_receipt(rejected)
        except (ValueError, UnicodeError):
            checked += 1
        else:
            raise ValueError('prior incompatible routing receipt accepted')
    result = {'protocol': 'rb100-routing-diagnostic-reader-v3', 'status': 'PASS_OBSERVATION_ONLY',
              'scope': SCOPE, 'layoutQualification': False, 'receiptSha256': digest,
              'explicitExpectedCandidate': args.candidate, 'explicitExpectedPlacementInput': args.placement_input,
              'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'jsonHelperSha256': hashlib.sha256(Path(__file__).with_name('read_diagnostic.py').read_bytes()).hexdigest(),
              'negativeCanaries': checked,
              'nullableNonSearchFixtures': {'QUALITY_LIMIT': 'PASS_SYNTHETIC_TRANSPORT_ONLY',
                                            'GRID_LIMIT': 'PASS_SYNTHETIC_TRANSPORT_ONLY'},
              'summary': summary, 'cases': cases}
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print('PASS: actual two-case routing observation; negative canaries=' +
          str(result['negativeCanaries']) + '; layout qualification=false')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, TypeError, KeyError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
