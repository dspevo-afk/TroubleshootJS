"""Read complete fixed-candidate P07 branch observations; grants no qualification."""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path
from read_diagnostic import strict_json, need, integer
from read_p07_policy import IDENTITY as POLICY_IDENTITY, LIMITS as POLICY_LIMITS, VARIANTS

PREFIX = 'RB100_P07_BRANCH_DIAGNOSTIC '
SUMMARY = 'PASS: RB100 P07 branch diagnostic contracts '
SCOPE = 'OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY'
IDENTITY = dict(POLICY_IDENTITY, protocolVersion=2)
LIMITS = {key: POLICY_LIMITS[key] for key in ['p07Expansions', 'p07BranchExpansions',
          'p07GridCells', 'p07Orderings', 'p07Vias', 'operationMs', 'workUnits', 'unitMs', 'cleanupMs']}
REASONS = {'FOUND', 'NO_PATH', 'TOTAL_SEARCH_LIMIT', 'BRANCH_SEARCH_LIMIT'}


def identity(value):
    need(type(value) is dict, 'missing identity')
    for key, expected in IDENTITY.items():
        need(type(value.get(key)) is type(expected) and value[key] == expected,
             'exact identity differs: ' + key)
    need(type(value.get('runId')) is str and re.fullmatch(
        r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', value['runId']) is not None,
        'missing invocation UUID')


def text_id(value):
    return type(value) is str and re.fullmatch(r'[A-Za-z0-9_+./:-]{1,128}', value) is not None


def fingerprint(value):
    return (type(value) is dict and integer(value.get('length'), 1, 1048576) and
            type(value.get('javaHash')) is str and
            re.fullmatch('[0-9a-f]{1,8}', value['javaHash']) is not None and
            type(value.get('sha256')) is str and
            re.fullmatch('[0-9a-f]{64}', value['sha256']) is not None)


def branches(facts):
    rows = facts.get('branches')
    need(type(rows) is list and integer(facts.get('branchCount'), 1, 3 * (246 - facts['nets']))
         and len(rows) == facts['branchCount'], 'complete branch census differs')
    prior_pass, expanded, seen, grid = 0, 0, set(), None
    prior_vias, prior_reason, reached = 0, None, {}
    for row in rows:
        need(type(row) is dict, 'missing branch snapshot')
        bounds = {'pass': (0, 2), 'gridWidth': (1, 250000), 'gridHeight': (1, 250000),
            'branchExpanded': (0, 100000), 'totalExpanded': (0, 1000000),
            'expandedTop': (0, 100000), 'expandedBottom': (0, 100000),
            'startX': (0, 16000), 'startY': (0, 16000),
            'rootX': (0, 16000), 'rootY': (0, 16000)}
        for key, (low, high) in bounds.items():
            need(integer(row.get(key), low, high), 'invalid branch integer: ' + key)
        shape = (row['gridWidth'], row['gridHeight'])
        need(shape[0] * shape[1] <= 250000 and (grid is None or shape == grid),
             'branch routing grid differs or exceeds cap')
        grid = shape
        maximum_distance = 10 * (shape[0] + shape[1] - 2)
        need(all(integer(row.get(key), 1, shape[0] * shape[1]) for key in
                 ['goalsTop', 'goalsBottom']), 'missing actual two-face goal census')
        need(all(text_id(row.get(key)) for key in ['netId', 'startPadId', 'rootPadId']),
             'invalid actual branch identity')
        branch_key = tuple(row[key] for key in ['pass', 'netId', 'startPadId', 'rootPadId'])
        need(branch_key not in seen, 'duplicate branch identity within one ordering')
        seen.add(branch_key)
        need(row['pass'] in (prior_pass, prior_pass + 1), 'noncontiguous/reversed pass order')
        if not expanded:
            need(row['pass'] == 0, 'first branch must belong to ordering zero')
        new_pass = prior_reason is None or row['pass'] != prior_pass
        need(new_pass or prior_reason == 'FOUND', 'branch continues after search rejection')
        need(integer(row.get('prefixViaCount'), 0, 48) and
             ((new_pass and row['prefixViaCount'] == 0) or
              (not new_pass and row['prefixViaCount'] >= prior_vias)),
             'published via prefix differs within an ordering')
        tree_key = (row['pass'], row['netId'])
        need(integer(row.get('reachedPadCount'), 1, 246) and
             row['reachedPadCount'] == 1 + reached.get(tree_key, 0),
             'connected-terminal tree census differs from prior found branches')
        need(row['goalsTop'] >= row['reachedPadCount'] and
             row['goalsBottom'] >= row['reachedPadCount'],
             'current fuller-policy goals omit a reached plated-pad face')
        prior_vias = row['prefixViaCount']
        prior_pass = row['pass']
        count = row['branchExpanded']
        need(row['expandedTop'] + row['expandedBottom'] == count,
             'expanded face census differs')
        expanded += count
        need(row['totalExpanded'] == expanded, 'cumulative expansion conservation differs')
        for key in ['frontierCount', 'bestStateCount']:
            need(integer(row.get(key), 0, 6 * count + 2), 'invalid queue/index census: ' + key)
        initial, minimum = row.get('initialManhattanDistance'), row.get('minimumExploredManhattanDistance')
        need(integer(initial, 0, maximum_distance) and initial % 10 == 0,
             'invalid initial relaxed-plane distance')
        root_distance = abs(row['startX'] - row['rootX']) + abs(row['startY'] - row['rootY'])
        need(initial <= root_distance, 'initial distance excludes the actual root goal')
        need((count == 0 and minimum == -1 and type(minimum) is int) or
             (count > 0 and integer(minimum, 0, initial) and minimum % 10 == 0),
             'expanded-state Manhattan minimum differs')
        reason = row.get('terminalReason')
        need(reason in REASONS, 'unknown search terminal reason')
        need(reason != 'FOUND' or (count > 0 and minimum == 0), 'FOUND lacks an expanded goal')
        need(reason != 'NO_PATH' or row['frontierCount'] == 0, 'NO_PATH has queued states')
        need(reason != 'BRANCH_SEARCH_LIMIT' or count == 100000, 'branch cap witness differs')
        need(reason != 'TOTAL_SEARCH_LIMIT' or expanded == 1000000, 'total cap witness differs')
        if reason == 'FOUND':
            reached[tree_key] = reached.get(tree_key, 0) + 1
        prior_reason = reason
    result = facts.get('routeResult')
    need(type(result) is dict and type(result.get('accepted')) is bool and
         integer(result.get('expansions'), 1, 1000000) and result['expansions'] == expanded and
         integer(result.get('orderings'), 1, 3) and result['orderings'] == prior_pass + 1 and
         integer(result.get('vias'), 0, 48) and integer(result.get('links'), 0, 0),
         'route result/complete branch work differs')
    outcome = result.get('outcome')
    need(type(outcome) is str and 0 < len(outcome) <= 256, 'missing bounded route outcome')
    if result['accepted']:
        final_branches = [row for row in rows if row['pass'] == prior_pass]
        need(outcome == 'SUCCESS' and rows[-1]['terminalReason'] == 'FOUND' and
             result['vias'] >= rows[-1]['prefixViaCount'] and
             len(final_branches) == 246 - facts['nets'] and
             all(row['terminalReason'] == 'FOUND' for row in final_branches),
             'accepted route does not finish its successful ordering')
    else:
        need(outcome == 'QUALITY_LIMIT' or re.fullmatch(
             r'[A-Za-z0-9_+./-]+:(?:NO_PATH|TOTAL_SEARCH_LIMIT|BRANCH_SEARCH_LIMIT|BRANCH_QUALITY_LIMIT)',
             outcome) is not None, 'unknown terminal route rejection')
        need(result['vias'] == 0, 'rejected route leaked published vias')
        if outcome.endswith((':NO_PATH', ':TOTAL_SEARCH_LIMIT', ':BRANCH_SEARCH_LIMIT')):
            need(outcome == rows[-1]['netId'] + ':' + rows[-1]['terminalReason'],
                 'rejected route differs from its terminal search branch')
        else:
            need(rows[-1]['terminalReason'] == 'FOUND' and
                 (outcome == 'QUALITY_LIMIT' or outcome == rows[-1]['netId'] + ':BRANCH_QUALITY_LIMIT'),
                 'publication/quality rejection lacks a found terminal branch')
            if outcome == 'QUALITY_LIMIT':
                final_branches = [row for row in rows if row['pass'] == prior_pass]
                need(len(final_branches) == 246 - facts['nets'] and
                     all(row['terminalReason'] == 'FOUND' for row in final_branches),
                     'route quality rejection lacks a complete found ordering')
    return result


def validate(cases, summary):
    identity(summary)
    need(summary.get('status') == 'PASS' and summary.get('scope') == SCOPE and
         integer(summary.get('cases'), 2, 2) and integer(summary.get('authoredVariants'), 2, 2) and
         integer(summary.get('fixedPlacementAttempt'), 4, 4) and
         summary.get('variants') == VARIANTS, 'terminal two-variant summary differs')
    need(type(cases) is list and len(cases) == 2, 'exact two-case census required')
    for index, row in enumerate(cases):
        need(type(row) is dict and integer(row.get('protocolVersion'), 2, 2) and
             row.get('runId') == summary['runId'] and row.get('status') == 'PASS' and
             row.get('scope') == SCOPE and row.get('layoutQualification') is False and
             row.get('cleanupStatus') == 'PASS' and
             row.get('failureType') == row.get('cleanupFailureType') == 'none' and
             row.get('failure') == row.get('cleanupFailure') == '', 'case status/cleanup differs')
        for key, low, high in [('operationMs', 0, 90000), ('cleanupMs', 0, 120000),
                               ('maxUnitMs', 0, 5000), ('workUnits', 1, 640)]:
            need(integer(row.get(key), low, high), 'invalid operation budget: ' + key)
        facts = row.get('reportFacts')
        identity(facts)
        need(facts['runId'] == summary['runId'] and facts.get('fixture') == 'AUTHORED_RB100' and
             facts.get('variant') == VARIANTS[index] and integer(facts.get('fixedPlacementAttempt'), 4, 4) and
             integer(facts.get('components'), 100, 100) and integer(facts.get('pads'), 246, 246) and
             integer(facts.get('nets'), 59 + index, 59 + index) and
             facts.get('boardIdentityStatus') == facts.get('solverGraphIdentityStatus') == 'PASS',
             'actual authored fixture identity differs')
        need(integer(facts.get('advanceCalls'), 1, row['workUnits']), 'invalid slice census')
        limits = facts.get('limits')
        need(type(limits) is dict and all(type(limits.get(key)) is type(value) and limits[key] == value
                                        for key, value in LIMITS.items()), 'frozen limits differ')
        need(fingerprint(facts.get('placementFingerprint')),
             'missing bounded immutable input fingerprint metadata')
        result = branches(facts)
        output = facts.get('layoutOutput')
        need(type(output) is dict and output.get('layoutReturned') is result['accepted'] and
             output.get('zeroOutput') is (not result['accepted']), 'terminal output ownership differs')
        counts = ['rawSegments', 'topTraceCount', 'bottomTraceCount', 'totalTraceCount',
                  'holeCount', 'viaCount', 'platedHoleCount', 'nonPlatedHoleCount']
        need(all(integer(output.get(key), 0, 1000000) for key in counts) and
             output['viaCount'] == result['vias'] and
             output['holeCount'] == output['viaCount'] + output['platedHoleCount'] + output['nonPlatedHoleCount'] and
             output['topTraceCount'] + output['bottomTraceCount'] == output['totalTraceCount'],
             'terminal output census differs')
        if result['accepted']:
            need(fingerprint(output.get('fingerprint')) and output['rawSegments'] > 0 and
                 output['totalTraceCount'] > 0 and type(output.get('outline')) is dict and
                 all(integer(output['outline'].get(key), 0 if key in ['x', 'y'] else 1, 16000)
                     for key in ['x', 'y', 'width', 'height']),
                 'routed observation lacks output metadata')
        else:
            need(output.get('fingerprint') is None and output.get('outline') is None and
                 all(output[key] == 0 for key in counts), 'rejected observation leaked output')
    census = [{'variant': facts['variant'], 'branchCount': facts['branchCount'],
               'expansions': facts['routeResult']['expansions'], 'outcome': facts['routeResult']['outcome']}
              for facts in [row['reportFacts'] for row in cases]]
    need(summary.get('variantCensus') == census, 'summary differs from actual per-variant case census')


def extract(lines):
    markers = [(kind, strict_json(line[len(prefix):])) for line in lines for kind, prefix in
               [('case', PREFIX), ('summary', SUMMARY)] if line.startswith(prefix)]
    need([kind for kind, _ in markers] == ['case', 'case', 'summary'], 'incomplete/reordered invocation framing')
    cases, summary = [value for _, value in markers[:2]], markers[-1][1]
    validate(cases, summary)
    return cases, summary


def negative_canaries(cases, summary):
    mutations = [lambda rows: rows.pop(), lambda rows: rows.__setitem__(1, copy.deepcopy(rows[0])),
        lambda rows: rows[0].__setitem__('cleanupStatus', 'FAIL'),
        lambda rows: rows[0].__setitem__('layoutQualification', True),
        lambda rows: rows[0].__setitem__('operationMs', True),
        lambda rows: rows[0]['reportFacts'].__setitem__('seed', 9007199254740993),
        lambda rows: rows[0]['reportFacts'].__setitem__('fixedPlacementAttempt', 0),
        lambda rows: rows[0]['reportFacts'].__setitem__('physicalIntentVersion', 8),
        lambda rows: rows[0]['reportFacts'].__setitem__('protocolVersion', 1),
        lambda rows: rows[0]['reportFacts'].__setitem__('placementDensityVersion', 3),
        lambda rows: rows[0]['reportFacts'].__setitem__('branchCount', 0),
        lambda rows: rows[0]['reportFacts']['branches'].pop(),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('expandedTop', -1),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('totalExpanded', 1000001),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('pass', 2),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('terminalReason', 'ASSUMED_UNREACHABLE'),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('minimumExploredManhattanDistance', 999999),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('frontierCount', True),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('prefixViaCount', 49),
        lambda rows: rows[0]['reportFacts']['branches'][0].__setitem__('reachedPadCount', 0),
        lambda rows: rows[0]['reportFacts']['routeResult'].__setitem__('expansions', 0),
        lambda rows: rows[0]['reportFacts']['placementFingerprint'].__setitem__('sha256', '')]
    count = 0
    for mutate in mutations:
        rows = copy.deepcopy(cases)
        mutate(rows)
        try:
            validate(rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid branch canary accepted')
    lines = [PREFIX + json.dumps(row) for row in cases] + [SUMMARY + json.dumps(summary)]
    for altered in [[lines[-1]] + lines[:-1], lines + [lines[-1]]]:
        try:
            extract(altered)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid branch framing accepted')
    for value in ['{"x":0,"x":1}', '{"x":NaN}']:
        try:
            strict_json(value)
        except ValueError:
            count += 1
        else:
            raise ValueError('malformed branch JSON accepted')
    for key, value in [('fixedPlacementAttempt', 0), ('variantCensus', [])]:
        altered = copy.deepcopy(summary)
        altered[key] = value
        try:
            validate(cases, altered)
        except ValueError:
            count += 1
        else:
            raise ValueError('contradictory branch summary accepted')
    for key, value in [('layoutVersion', 24),
                       ('dependencyEpoch', 'tsj-generation-dependencies-v25')]:
        altered_rows = copy.deepcopy(cases)
        altered_rows[0]['reportFacts'][key] = value
        try:
            validate(altered_rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('immediate-prior branch facts accepted: ' + key)
        altered_summary = copy.deepcopy(summary)
        altered_summary[key] = value
        try:
            validate(cases, altered_summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('immediate-prior branch summary accepted: ' + key)
    rows, altered = copy.deepcopy(cases), copy.deepcopy(summary)
    rows[0]['reportFacts']['routeResult']['outcome'] = 'SYNTHETIC_WRONG_NET:NO_PATH'
    altered['variantCensus'][0]['outcome'] = 'SYNTHETIC_WRONG_NET:NO_PATH'
    try:
        validate(rows, altered)
    except ValueError:
        count += 1
    else:
        raise ValueError('route rejection contradicting the terminal branch accepted')
    return count


def routed_transport_canary(cases, summary):
    """Exercise result framing; synthetic coordinates are not physical evidence."""
    rows, terminal = copy.deepcopy(cases), copy.deepcopy(summary)
    facts = rows[0]['reportFacts']
    count = 246 - facts['nets']
    template = facts['branches'][0]
    events = []
    for index in range(count):
        branch = copy.deepcopy(template)
        branch.update({'pass': 0, 'netId': 'SYNTHETIC_NET', 'rootPadId': 'SYNTHETIC_ROOT',
            'startPadId': 'SYNTHETIC_' + str(index), 'branchExpanded': 1, 'totalExpanded': index + 1,
            'expandedTop': 1, 'expandedBottom': 0, 'minimumExploredManhattanDistance': 0,
            'frontierCount': 0, 'bestStateCount': 1, 'terminalReason': 'FOUND',
            'prefixViaCount': 0, 'reachedPadCount': index + 1,
            'goalsTop': index + 1, 'goalsBottom': index + 1})
        events.append(branch)
    facts.update(branches=events, branchCount=count,
        routeResult={'accepted': True, 'outcome': 'SUCCESS', 'expansions': count,
                     'orderings': 1, 'vias': 0, 'links': 0},
        layoutOutput={'layoutReturned': True, 'zeroOutput': False,
            'fingerprint': {'length': 1, 'javaHash': '1', 'sha256': 'a' * 64},
            'outline': {'x': 0, 'y': 0, 'width': 100, 'height': 100},
            'rawSegments': 2, 'topTraceCount': 1, 'bottomTraceCount': 1, 'totalTraceCount': 2,
            'holeCount': 0, 'viaCount': 0, 'platedHoleCount': 0, 'nonPlatedHoleCount': 0})
    rows[0]['phase'] = 'SYNTHETIC_ROUTED_TRANSPORT_ONLY'
    terminal['variantCensus'][0].update(branchCount=count, expansions=count, outcome='SUCCESS')
    validate(rows, terminal)
    negatives = 0
    for truncate in [False, True]:
        invalid, invalid_summary = copy.deepcopy(rows), copy.deepcopy(terminal)
        invalid_facts = invalid[0]['reportFacts']
        if truncate:
            invalid_facts['branches'].pop()
            invalid_facts['branchCount'] -= 1
            invalid_facts['routeResult']['expansions'] -= 1
            invalid_summary['variantCensus'][0].update(branchCount=count - 1, expansions=count - 1)
        else:
            invalid_facts['layoutOutput']['fingerprint']['sha256'] = ''
        try:
            validate(invalid, invalid_summary)
        except ValueError:
            negatives += 1
        else:
            raise ValueError('incomplete routed census or output digest accepted')
    quality, quality_summary = copy.deepcopy(rows), copy.deepcopy(terminal)
    quality_facts = quality[0]['reportFacts']
    quality_facts['routeResult'].update(accepted=False, outcome='QUALITY_LIMIT')
    quality_facts['layoutOutput'] = {'layoutReturned': False, 'zeroOutput': True,
        'fingerprint': None, 'outline': None, 'rawSegments': 0, 'topTraceCount': 0,
        'bottomTraceCount': 0, 'totalTraceCount': 0, 'holeCount': 0, 'viaCount': 0,
        'platedHoleCount': 0, 'nonPlatedHoleCount': 0}
    quality_summary['variantCensus'][0]['outcome'] = 'QUALITY_LIMIT'
    validate(quality, quality_summary)
    quality_facts['branches'].pop()
    quality_facts['branchCount'] -= 1
    quality_facts['routeResult']['expansions'] -= 1
    quality_summary['variantCensus'][0].update(branchCount=count - 1, expansions=count - 1)
    try:
        validate(quality, quality_summary)
    except ValueError:
        return negatives + 1
    raise ValueError('truncated final ordering accepted as route quality rejection')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--rejected-receipt', type=Path, action='append', default=[])
    args = parser.parse_args()
    need(not args.output.exists(), 'refusing existing output')
    raw = args.receipt.read_bytes()
    need(len(raw) <= 4 * 1024 * 1024, 'branch transport exceeds 4MiB')
    cases, summary = extract(raw.decode('utf-8-sig').splitlines())
    negatives = negative_canaries(cases, summary) + routed_transport_canary(cases, summary)
    for path in args.rejected_receipt:
        try:
            extract(path.read_text(encoding='utf-8-sig').splitlines())
        except (ValueError, UnicodeError):
            negatives += 1
        else:
            raise ValueError('prior incompatible branch receipt accepted')
    result = {'protocol': 'rb100-p07-branch-reader-v2', 'status': 'PASS_OBSERVATION_ONLY',
        'scope': SCOPE, 'layoutQualification': False, 'negativeCanaries': negatives,
        'syntheticTransportFixtures': {'ROUTED': 'PASS_TRANSPORT_ONLY', 'QUALITY_LIMIT': 'PASS_TRANSPORT_ONLY'},
        'receiptSha256': hashlib.sha256(raw).hexdigest(),
        'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'helperSha256': {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                        for name in ['read_diagnostic.py', 'read_p07_policy.py', 'read_routing_diagnostic.py']},
        'summary': summary, 'cases': cases}
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print('PASS: fixed4 P07 branch observation; negative canaries=' + str(negatives) +
          '; physical qualification=false')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, KeyError, TypeError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
