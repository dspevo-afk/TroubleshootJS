"""Read the private P07 policy observation; this grants no physical qualification."""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path
from read_diagnostic import strict_json, need, integer
from read_routing_diagnostic import java_hash

PREFIX = 'RB100_P07_POLICY_DIAGNOSTIC '
SUMMARY = 'PASS: RB100 P07 policy diagnostic contracts '
SCOPE = 'OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY'
VARIANTS = ['SHARED_HYSTERETIC_LINEAR', 'SEPARATE_DIRECT_SWITCHING']
IDENTITY = {'protocolVersion': 2, 'layoutVersion': 25,
            'dependencyEpoch': 'tsj-generation-dependencies-v26', 'planVersion': 1,
            'modelVersion': 4, 'physicalIntentVersion': 9, 'seed': '9007199254740993',
            'placementSeed': '-6587958367974952701', 'routingSeed': '8445301674744508279',
            'physicalPolicyId': 'MEDIUM_BOARD', 'physicalPolicyVersion': 1,
            'placementDensityVersion': 14, 'requiresTwoLayerRouting': True,
            'routingLayer': 'BOTTOM', 'traceWidthProfile': 'ROLE_TRACE@1',
            'placementPackingVersion': 4, 'layoutQualification': False}
LIMITS = {'placementCandidates': 6, 'routeCandidates': 3, 'p07Expansions': 1000000,
          'p07BranchExpansions': 100000, 'p07GridCells': 250000, 'p07Orderings': 3,
          'p07Vias': 48, 'outerPlacements': 80, 'outerRouteAttempts': 800,
          'outerRouteExpansions': 160000000, 'outerAccessCells': 700000,
          'operationMs': 90000, 'workUnits': 640, 'unitMs': 5000, 'cleanupMs': 120000}


def identity(value):
    need(type(value) is dict, 'missing identity object')
    for key, expected in IDENTITY.items():
        need(type(value.get(key)) is type(expected) and value[key] == expected,
             'current exact identity differs: ' + key)
    need(type(value.get('runId')) is str and
         re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', value['runId']) is not None,
         'missing invocation identity')


def complete_text(obj, name='canonical', maximum=65536):
    text = obj.get(name)
    need(type(text) is str and 0 < len(text) <= maximum, 'missing bounded complete ' + name)
    digest, length = java_hash(text)
    need(integer(obj.get(name + 'Length'), 1, maximum) and obj[name + 'Length'] == length and
         obj.get(name + 'JavaHash') == digest, 'text completeness metadata differs: ' + name)
    return text


def vector(value):
    return '[' + ', '.join(str(item) for item in value) + ']'


ORDERING_RECEIPT_FIELDS = {
    'attempt', 'startedOrderings', 'completedOrderings', 'yieldedOrderings',
    'finalizedOrderings', 'selectedOrdering', 'expansions', 'guidanceStarted',
    'comparisonComplete', 'termination'}
FIXED_ORDERING_TERMINATIONS = {
    'ORDERINGS_COMPLETE', 'TOTAL_SEARCH_LIMIT', 'FIRST_SUCCESS_UNGUIDED'}
PLAIN_ORDERING_REASONS = {'GRID_LIMIT', 'QUALITY_LIMIT', 'NO_PATH'}
NET_ORDERING_REASONS = {
    'NO_PATH', 'TOTAL_SEARCH_LIMIT', 'BRANCH_SEARCH_LIMIT', 'BRANCH_QUALITY_LIMIT'}


def canonical_termination(value):
    need(type(value) is str and 0 < len(value) <= 256,
         'invalid bounded ordering termination')
    encoded = value.encode('utf-16-le', 'surrogatepass')
    need(0 < len(encoded) // 2 <= 256,
         'ordering termination exceeds Java UTF-16 length bound')
    result = []
    for offset in range(0, len(encoded), 2):
        unit = encoded[offset] | (encoded[offset + 1] << 8)
        if (ord('A') <= unit <= ord('Z') or ord('a') <= unit <= ord('z') or
                ord('0') <= unit <= ord('9') or unit in (ord('_'), ord(':'), ord('.'), ord('-'))):
            result.append(chr(unit))
        else:
            result.append('\\u%04x' % unit)
    return ''.join(result)


def ordering_receipt_canonical(receipt):
    need(type(receipt) is dict and set(receipt) == ORDERING_RECEIPT_FIELDS,
         'ordering-search receipt fields differ')
    integer_fields = ['attempt', 'startedOrderings', 'completedOrderings',
                      'yieldedOrderings', 'finalizedOrderings', 'selectedOrdering', 'expansions']
    need(all(type(receipt.get(key)) is int for key in integer_fields),
         'ordering-search integer field type differs')
    need(type(receipt.get('guidanceStarted')) is bool and
         type(receipt.get('comparisonComplete')) is bool,
         'ordering-search boolean field type differs')
    return ('attempt=' + str(receipt['attempt']) +
        ';startedOrderings=' + str(receipt['startedOrderings']) +
        ';completedOrderings=' + str(receipt['completedOrderings']) +
        ';yieldedOrderings=' + str(receipt['yieldedOrderings']) +
        ';finalizedOrderings=' + str(receipt['finalizedOrderings']) +
        ';selectedOrdering=' + str(receipt['selectedOrdering']) +
        ';expansions=' + str(receipt['expansions']) +
        ';guidanceStarted=' + str(receipt['guidanceStarted']).lower() +
        ';comparisonComplete=' + str(receipt['comparisonComplete']).lower() +
        ';termination=' + canonical_termination(receipt['termination']))


def ordering_receipts_canonical(receipts):
    """Statistics uses a comma-only separator for this new typed receipt vector."""
    return '[' + ','.join(ordering_receipt_canonical(receipt) for receipt in receipts) + ']'


def known_ordering_termination(value):
    if value in FIXED_ORDERING_TERMINATIONS or value in PLAIN_ORDERING_REASONS:
        return True
    if ':' not in value:
        return False
    prefix, reason = value.rsplit(':', 1)
    return bool(prefix) and reason in NET_ORDERING_REASONS


def validate_ordering_receipts(receipts, receipt_count, expected_attempts):
    need(type(receipts) is list and integer(receipt_count, 0, 3) and
         receipt_count == len(receipts),
         'ordering-search receipt count differs from its typed vector')
    if not receipts:
        return receipts  # Empty legacy vectors carry no per-ordering census.
    need(receipt_count == len(expected_attempts),
         'nonempty ordering-search receipt count differs from actual P07 requests')
    seen = set()
    for index, receipt in enumerate(receipts):
        ordering_receipt_canonical(receipt)
        for key, low, high in [
                ('attempt', 0, 5), ('startedOrderings', 1, 3),
                ('completedOrderings', 1, 3), ('yieldedOrderings', 0, 3),
                ('finalizedOrderings', 0, 3), ('selectedOrdering', -1, 2),
                ('expansions', 0, 1000000)]:
            need(integer(receipt.get(key), low, high),
                 'invalid ordering-search receipt counter: ' + key)
        attempt = receipt['attempt']
        need(attempt == expected_attempts[index] and attempt not in seen,
             'ordering-search receipt attempts differ from unique P07 request census')
        seen.add(attempt)
        started, completed = receipt['startedOrderings'], receipt['completedOrderings']
        yielded, finalized = receipt['yieldedOrderings'], receipt['finalizedOrderings']
        need(completed <= started and yielded <= completed and finalized <= yielded,
             'ordering-search receipt work counts are inconsistent')
        selected = receipt['selectedOrdering']
        need(selected < 0 or (selected < started and finalized > 0),
             'selected ordering is outside started/finalized receipt work')
        termination = receipt['termination']
        need(known_ordering_termination(termination),
             'unknown ordering-search termination')
        capped = termination == 'TOTAL_SEARCH_LIMIT' or termination.endswith(':TOTAL_SEARCH_LIMIT')
        if termination == 'FIRST_SUCCESS_UNGUIDED':
            need(not receipt['guidanceStarted'] and not receipt['comparisonComplete'] and
                 yielded == 1,
                 'unguided first-success receipt status differs')
        elif termination == 'ORDERINGS_COMPLETE':
            need(receipt['comparisonComplete'] and started == completed == 3 and yielded > 0,
                 'completed ordering-search receipt census differs')
        elif capped:
            need(not receipt['comparisonComplete'],
                 'total-cap termination is incorrectly reported comparison-complete')
        else:
            need(receipt['comparisonComplete'] and started == completed == 3 and yielded == 0,
                 'completed no-yield rejection census differs')
    return receipts


def ordering_search_reader_canaries():
    """Exercise receipt framing/status validation only; this creates no route evidence."""
    complete = {'attempt': 0, 'startedOrderings': 3, 'completedOrderings': 3,
        'yieldedOrderings': 2, 'finalizedOrderings': 2, 'selectedOrdering': 1,
        'expansions': 300, 'guidanceStarted': True, 'comparisonComplete': True,
        'termination': 'ORDERINGS_COMPLETE'}
    unguided = {'attempt': 2, 'startedOrderings': 1, 'completedOrderings': 1,
        'yieldedOrderings': 1, 'finalizedOrderings': 0, 'selectedOrdering': -1,
        'expansions': 200, 'guidanceStarted': False, 'comparisonComplete': False,
        'termination': 'FIRST_SUCCESS_UNGUIDED'}
    valid = [complete, unguided]
    validate_ordering_receipts(valid, 2, [0, 2])
    expected = ('attempt=0;startedOrderings=3;completedOrderings=3;yieldedOrderings=2;' +
        'finalizedOrderings=2;selectedOrdering=1;expansions=300;guidanceStarted=true;' +
        'comparisonComplete=true;termination=ORDERINGS_COMPLETE')
    need(ordering_receipt_canonical(complete) == expected,
         'ordering-search canonical field order differs')
    two_receipt_canonical = (
        ';orderingSearchReceipts=[' + expected + ',' +
        ordering_receipt_canonical(unguided) + ']')
    need(';orderingSearchReceipts=' + ordering_receipts_canonical([complete, unguided]) ==
         two_receipt_canonical and ', ' not in two_receipt_canonical,
         'two-receipt canonical vector must use the production comma-only separator')
    need(vector(['left', 'right']) == '[left, right]',
         'existing Vector canonical spacing changed')
    need(canonical_termination('NET A:NO_PATH') == r'NET\u0020A:NO_PATH',
         'ordering-search termination escaping differs')
    need(canonical_termination('A' * 256) == 'A' * 256,
         '256-unit ordering termination boundary differs')
    bound_rejected = 0
    try:
        canonical_termination('A' * 257)
    except ValueError:
        bound_rejected = 1
    else:
        raise ValueError('257-unit ordering termination accepted')
    need(ordering_receipt_canonical(unguided).endswith(
         ';guidanceStarted=false;comparisonComplete=false;termination=FIRST_SUCCESS_UNGUIDED'),
         'unguided first-success receipt canonical differs')
    no_yield = {'attempt': 1, 'startedOrderings': 3, 'completedOrderings': 3,
        'yieldedOrderings': 0, 'finalizedOrderings': 0, 'selectedOrdering': -1,
        'expansions': 321, 'guidanceStarted': True, 'comparisonComplete': True,
        'termination': 'NET A:BRANCH_SEARCH_LIMIT'}
    validate_ordering_receipts([no_yield], 1, [1])
    validate_ordering_receipts([], 0, [0, 2])
    # These are the exact per-receipt Java canonical strings emitted by the
    # completed SAVED7_ROOT42_ACTUAL_GENERATOR native record. The record does
    # not carry the enclosing Statistics.toCanonical() text, so compare the
    # reader's list-framing against those captured production strings and the
    # source-owned comma-only formatter, without claiming full-field parity.
    generator_receipts = [
        {'attempt': 4, 'startedOrderings': 3, 'completedOrderings': 3,
         'yieldedOrderings': 3, 'finalizedOrderings': 3, 'selectedOrdering': 2,
         'expansions': 683850, 'guidanceStarted': True, 'comparisonComplete': True,
         'termination': 'ORDERINGS_COMPLETE'},
        {'attempt': 2, 'startedOrderings': 1, 'completedOrderings': 1,
         'yieldedOrderings': 1, 'finalizedOrderings': 1, 'selectedOrdering': -1,
         'expansions': 168757, 'guidanceStarted': False, 'comparisonComplete': False,
         'termination': 'FIRST_SUCCESS_UNGUIDED'},
        {'attempt': 3, 'startedOrderings': 3, 'completedOrderings': 3,
         'yieldedOrderings': 1, 'finalizedOrderings': 1, 'selectedOrdering': -1,
         'expansions': 919006, 'guidanceStarted': True, 'comparisonComplete': True,
         'termination': 'ORDERINGS_COMPLETE'}]
    validate_ordering_receipts(generator_receipts, 3, [4, 2, 3])
    captured_generator_receipts = [
        'attempt=4;startedOrderings=3;completedOrderings=3;yieldedOrderings=3;finalizedOrderings=3;selectedOrdering=2;expansions=683850;guidanceStarted=true;comparisonComplete=true;termination=ORDERINGS_COMPLETE',
        'attempt=2;startedOrderings=1;completedOrderings=1;yieldedOrderings=1;finalizedOrderings=1;selectedOrdering=-1;expansions=168757;guidanceStarted=false;comparisonComplete=false;termination=FIRST_SUCCESS_UNGUIDED',
        'attempt=3;startedOrderings=3;completedOrderings=3;yieldedOrderings=1;finalizedOrderings=1;selectedOrdering=-1;expansions=919006;guidanceStarted=true;comparisonComplete=true;termination=ORDERINGS_COMPLETE']
    need([ordering_receipt_canonical(receipt) for receipt in generator_receipts] ==
         captured_generator_receipts,
         'captured generator receipt strings differ from typed receipt canonicalization')
    actual_generator_list = ';orderingSearchReceipts=' + ordering_receipts_canonical(generator_receipts)
    expected_generator_list = ';orderingSearchReceipts=[' + ','.join(captured_generator_receipts) + ']'
    need(actual_generator_list == expected_generator_list,
         'three-receipt canonical list differs from captured Java generator receipt strings')
    need(canonical_termination(no_yield['termination']) ==
         r'NET\u0020A:BRANCH_SEARCH_LIMIT',
         'net-prefixed no-yield reason encoding differs')
    rejected = bound_rejected
    cases = []
    cases.append(([complete, unguided], 4, [0, 2]))
    duplicate = copy.deepcopy(valid)
    duplicate[1]['attempt'] = 0
    cases.append((duplicate, 2, [0, 2]))
    bad_type = copy.deepcopy(valid)
    bad_type[0]['guidanceStarted'] = 1
    cases.append((bad_type, 2, [0, 2]))
    cap_complete = copy.deepcopy(valid)
    cap_complete[0]['termination'] = 'TOTAL_SEARCH_LIMIT'
    cap_complete[0]['comparisonComplete'] = True
    cases.append((cap_complete, 2, [0, 2]))
    selected_bad = copy.deepcopy(valid)
    selected_bad[0]['selectedOrdering'] = 3
    cases.append((selected_bad, 2, [0, 2]))
    selected_without_final = copy.deepcopy(valid)
    selected_without_final[0]['finalizedOrderings'] = 0
    cases.append((selected_without_final, 2, [0, 2]))
    selected_not_started = copy.deepcopy(valid)
    selected_not_started[1]['selectedOrdering'] = 1
    cases.append((selected_not_started, 2, [0, 2]))
    unknown = copy.deepcopy(valid)
    unknown[0]['termination'] = 'UNKNOWN_TERMINATION'
    cases.append((unknown, 2, [0, 2]))
    unknown_suffix = copy.deepcopy(valid)
    unknown_suffix[0]['termination'] = 'SYNTHETIC_NET:NOT_A_ROUTER_REASON'
    cases.append((unknown_suffix, 2, [0, 2]))
    partial_reason = copy.deepcopy(valid)
    partial_reason[0].update(startedOrderings=2, completedOrderings=2,
                             yieldedOrderings=0, finalizedOrderings=0,
                             selectedOrdering=-1, comparisonComplete=False,
                             termination='NO_PATH')
    cases.append((partial_reason, 2, [0, 2]))
    initial_no_path = copy.deepcopy(valid)
    initial_no_path[0].update(startedOrderings=0, completedOrderings=0,
                              yieldedOrderings=0, finalizedOrderings=0,
                              selectedOrdering=-1, guidanceStarted=False,
                              comparisonComplete=False, termination='NO_PATH')
    cases.append((initial_no_path, 2, [0, 2]))
    zero_cap = copy.deepcopy(valid)
    zero_cap[0].update(startedOrderings=0, completedOrderings=0,
                       yieldedOrderings=0, finalizedOrderings=0,
                       selectedOrdering=-1, guidanceStarted=True,
                       comparisonComplete=False, termination='TOTAL_SEARCH_LIMIT')
    cases.append((zero_cap, 2, [0, 2]))
    yielded_reason = copy.deepcopy(valid)
    yielded_reason[0].update(yieldedOrderings=1, finalizedOrderings=0,
                             selectedOrdering=-1, termination='NO_PATH')
    cases.append((yielded_reason, 2, [0, 2]))
    over_yield = copy.deepcopy(valid)
    over_yield[0]['yieldedOrderings'] = 4
    cases.append((over_yield, 2, [0, 2]))
    over_budget = copy.deepcopy(valid)
    over_budget[0]['expansions'] = 1000001
    cases.append((over_budget, 2, [0, 2]))
    wrong_order = copy.deepcopy(valid)
    cases.append((wrong_order, 2, [2, 0]))
    for receipts, count, attempts in cases:
        try:
            validate_ordering_receipts(receipts, count, attempts)
        except ValueError:
            rejected += 1
        else:
            raise ValueError('invalid ordering-search receipt accepted')
    return rejected + 13


def policy_canonical(stats, probe):
    selected = stats['selectedRoutePolicy']
    result = ('MEDIUM_BOARD@1;outcome=' + stats['outcome'] + ';selected=' +
        ('null' if selected is None else selected) + ';placements=' + str(stats['placementCandidates']) +
        ';placementRejects=' + str(stats['placementRejections']) +
        ';placementEvaluations=' + str(stats['placementEvaluations']) +
        ';routes=' + str(stats['routeAttempts']) + ';routingWork=' + str(stats['routingOrders']) +
        '/' + str(stats['routingExpansions']) + ';p05=0/0;p07=' + str(stats['twoLayerAttempts']) +
        '/' + str(stats['twoLayerSuccesses']) + ';selectedAttempt=' + str(stats['selectedPlacementAttempt']) +
        ';ranked=' + vector(stats['rankedPlacementAttempts']) +
        ';placementScores=' + vector(stats['placementScores']) +
        ';selectedScore=' + str(stats['selectedPlacementScore']) +
        ';selectedQualityMilli=' + str(stats['selectedRouteQualityMilli']) +
        ';routeOutcomes=' + vector(stats['routeOutcomes']))
    if 'routeEligiblePlacementAttempts' in stats:
        result += ';routeEligiblePlacementAttempts=' + vector(stats['routeEligiblePlacementAttempts'])
        result += ';routeEligibilitySkips=' + vector(stats['routeEligibilitySkips'])
    if probe is not None:
        result += ';packingProbe=' + probe['canonical']
    receipts = stats.get('orderingSearchReceipts', [])
    if receipts:
        result += ';orderingSearchReceipts=' + ordering_receipts_canonical(receipts)
    return result


def set_text_metadata(obj, name):
    obj[name + 'JavaHash'], obj[name + 'Length'] = java_hash(obj[name])


def validate_probe(probe):
    if probe is None:
        return 0, 0
    need(type(probe) is dict, 'invalid optional probe')
    for key, low, high in [('attempts', 0, 1), ('orderings', 0, 3),
                           ('expansions', 0, 1000000), ('millis', 0, 90000)]:
        need(integer(probe.get(key), low, high), 'invalid probe counter: ' + key)
    need(probe.get('routePolicy') == 'P07_FULLER_TWO_LAYER' and
         type(probe.get('disposition')) is str and probe['disposition'] != '', 'probe route/disposition differs')
    for key in ['baselineGeometry', 'selectedGeometry']:
        need(type(probe.get(key)) is str and re.fullmatch(r'[0-9a-f]{64}', probe[key]) is not None,
             'missing exact probe geometry key')
    expected = ('normal-packing@3;attempts=' + str(probe['attempts']) +
                ';orderings=' + str(probe['orderings']) + ';expansions=' + str(probe['expansions']) +
                ';millis=' + str(probe['millis']) + ';route=' + probe['routePolicy'] +
                ';disposition=' + probe['disposition'] + ';baseline=' + probe['baselineGeometry'] +
                ';selected=' + probe['selectedGeometry'])
    need(complete_text(probe) == expected, 'probe typed/canonical data differs')
    need(type(probe.get('metricEvidence')) is dict, 'missing actual optional-probe evidence state')
    disposition, evidence = probe['disposition'], probe['metricEvidence']
    allowed = {'NO_CHANGE': {'NOT_MEASURED'}, 'NO_HEADROOM': {'NOT_MEASURED', 'BASELINE_UNAVAILABLE'},
               'ROUTE_REJECTED': {'BASELINE_ONLY'}, 'KEPT': {'CANDIDATE_UNAVAILABLE', 'COMPARED'},
               'SELECTED': {'COMPARED'}}
    need(disposition in allowed and evidence.get('state') in allowed[disposition],
         'probe disposition/evidence differs')
    need((probe['attempts'] == 0 and disposition in {'NO_CHANGE', 'NO_HEADROOM'} and
          probe['orderings'] == probe['expansions'] == probe['millis'] == 0) or
         (probe['attempts'] == 1 and disposition in {'KEPT', 'SELECTED', 'ROUTE_REJECTED'}),
         'probe attempts/disposition/work differs')
    need((probe['baselineGeometry'] != probe['selectedGeometry']) == (disposition == 'SELECTED'),
         'probe geometry/disposition differs')
    state = evidence['state']
    for key in ['baseline', 'candidate']:
        measured = key == 'baseline' and state in {'BASELINE_ONLY', 'CANDIDATE_UNAVAILABLE', 'COMPARED'} or \
                   key == 'candidate' and state == 'COMPARED'
        metrics = evidence.get(key)
        need((type(metrics) is dict and all(integer(metrics.get(name), 0, (1 << 63) - 1)
              for name in ['area', 'copper', 'vias', 'protectedVoid'])) if measured else metrics is None,
             'probe metric state/value differs: ' + key)
    unavailable = state in {'BASELINE_UNAVAILABLE', 'CANDIDATE_UNAVAILABLE'}
    reason = evidence.get('unavailableReason')
    need((type(reason) is str and 0 < len(reason) <= 160) if unavailable else reason is None,
         'probe missing/unexpected metric-unavailable reason')
    if state == 'COMPARED':
        before, after = evidence['baseline'], evidence['candidate']
        improves = after['area'] < before['area'] and all(after[key] <= before[key]
                    for key in ['copper', 'vias', 'protectedVoid'])
        need(improves == (disposition == 'SELECTED'), 'probe strict metric decision differs')
    return probe['orderings'], probe['expansions']


def validate_stats(facts):
    stats = facts.get('policyStatistics')
    need(type(stats) is dict and stats.get('policyIdentity') == 'MEDIUM_BOARD@1' and
         stats.get('outcome') in {'SUCCESS', 'REJECTED'}, 'missing terminal typed policy statistics')
    bounds = [('placementCandidates', 6, 6), ('placementRejections', 0, 6),
              ('placementEvaluations', 0, 2147483647), ('routeAttempts', 1, 3),
              ('oneFaceAttempts', 0, 0), ('oneFaceSuccesses', 0, 0),
              ('twoLayerAttempts', 1, 3), ('twoLayerSuccesses', 0, 3),
              ('selectedPlacementAttempt', -1, 5), ('rankedPlacementCount', 0, 6),
              ('placementScoreCount', 0, 6), ('routeOutcomesCount', 0, 3),
              ('routingOrders', 0, 800), ('routingExpansions', 0, 160000000),
              ('selectedPlacementScore', -1, (1 << 63) - 1),
              ('selectedRouteQualityMilli', -1, (1 << 63) - 1)]
    for key, low, high in bounds:
        need(integer(stats.get(key), low, high), 'invalid typed policy counter: ' + key)
    ranks, scores, outcomes = [stats.get(key) for key in
                               ['rankedPlacementAttempts', 'placementScores', 'routeOutcomes']]
    need(type(ranks) is list and all(integer(item, 0, 5) for item in ranks) and
         len(ranks) == len(set(ranks)) == stats['rankedPlacementCount'] ==
         stats['placementCandidates'] - stats['placementRejections'], 'ranked candidate census differs')
    need(type(scores) is list and len(scores) == len(ranks) == stats['placementScoreCount'] and
         all(type(item) is str and 0 < len(item) <= 4096 for item in scores), 'placement-score census differs')
    need(all(score.startswith(str(rank) + '{') and score.endswith('}')
             for rank, score in zip(ranks, scores, strict=True)), 'scores do not follow actual ranked candidates')
    eligible, skips = stats.get('routeEligiblePlacementAttempts'), stats.get('routeEligibilitySkips')
    need(type(eligible) is list and all(integer(item, 0, 5) for item in eligible) and
         integer(stats.get('routeEligiblePlacementAttemptCount'), 0, 6) and
         len(eligible) == len(set(eligible)) == stats['routeEligiblePlacementAttemptCount'] and
         set(eligible) <= set(ranks), 'eligible placement census differs')
    need(type(skips) is list and integer(stats.get('routeEligibilitySkipCount'), 0, 6) and
         len(skips) == stats['routeEligibilitySkipCount'], 'grid skip census differs')
    skipped_ids = set()
    for skip in skips:
        need(type(skip) is str and len(skip) <= 256, 'invalid grid skip record')
        match = re.fullmatch(r'attempt=(\d+);width=(-?\d+);height=(-?\d+);cells=(-?\d+);reason=GRID_LIMIT', skip)
        need(match is not None, 'incomplete grid skip record')
        attempt, width, height, cells = map(int, match.groups())
        need(attempt in ranks and attempt not in skipped_ids and attempt not in eligible and
             cells == width * height and (width < 1 or height < 1 or cells > 250000),
             'grid skip does not describe an ineligible ranked candidate')
        skipped_ids.add(attempt)
    need(set(eligible) | skipped_ids == set(ranks), 'eligibility partition omits ranked candidates')
    need(type(outcomes) is list and len(outcomes) == stats['routeOutcomesCount'] ==
         stats['routeAttempts'] == stats['twoLayerAttempts'] == min(3, len(eligible)),
         'main routing attempt/outcome census differs')
    successes = 0
    successful_attempts = set()
    attempted = set()
    outcome_attempts = []
    outcome_by_attempt = {}
    for index, outcome in enumerate(outcomes):
        need(type(outcome) is str and 0 < len(outcome) <= 4096, 'missing bounded P07 outcome')
        match = re.fullmatch(r'P07_FULLER_TWO_LAYER@([0-5])=(.+)', outcome)
        need(match is not None, 'invalid P07 candidate outcome framing')
        attempt, tail = int(match[1]), match[2]
        need(attempt in eligible and attempt not in attempted, 'P07 requested an ineligible/duplicate candidate')
        need(successes > 0 or attempt == eligible[index], 'P07 skipped eligible priority before success')
        attempted.add(attempt)
        outcome_attempts.append(attempt)
        outcome_by_attempt[attempt] = tail
        need(tail == 'SUCCESS' or tail.startswith('REJECTED:'), 'unknown P07 outcome')
        successes += tail == 'SUCCESS'
        if tail == 'SUCCESS':
            successful_attempts.add(attempt)
    need(successes == stats['twoLayerSuccesses'], 'P07 success census differs')
    has_receipt_fields = ('orderingSearchReceipts' in stats or
                          'orderingSearchReceiptsCount' in stats)
    if has_receipt_fields:
        need('orderingSearchReceipts' in stats and 'orderingSearchReceiptsCount' in stats,
             'ordering-search receipt fields are incomplete')
        receipts = validate_ordering_receipts(
            stats['orderingSearchReceipts'], stats['orderingSearchReceiptsCount'], outcome_attempts)
        for receipt in receipts:
            need((receipt['finalizedOrderings'] > 0) ==
                 (outcome_by_attempt[receipt['attempt']] == 'SUCCESS'),
                 'candidate outcome differs from finalized ordering-search routes')
        selected_receipts = [receipt for receipt in receipts
                             if receipt['selectedOrdering'] >= 0]
        need(all(outcome_by_attempt[receipt['attempt']] == 'SUCCESS' and
                 receipt['finalizedOrderings'] > 0 for receipt in selected_receipts),
             'request-local winning order has no successful finalized route')
        if receipts and stats['outcome'] == 'SUCCESS' and \
                stats['selectedRoutePolicy'] == 'P07_FULLER_TWO_LAYER':
            final_receipt = next((receipt for receipt in receipts
                                  if receipt['attempt'] == stats['selectedPlacementAttempt']), None)
            need(final_receipt is not None and final_receipt['selectedOrdering'] >= 0,
                 'selected P07 request/order differs from ordering-search receipts')
    else:
        receipts = []
    probe_orders, probe_expansions = validate_probe(facts.get('packingProbe'))
    if has_receipt_fields and receipts:
        need(sum(receipt['startedOrderings'] for receipt in receipts) + probe_orders ==
             stats['routingOrders'] and
             sum(receipt['expansions'] for receipt in receipts) + probe_expansions ==
             stats['routingExpansions'],
             'ordering-search receipt work differs from aggregate P07/probe work')
    for key, aggregate, extra, maximum in [
            ('primaryRoutingOrders', stats['routingOrders'], probe_orders, 9),
            ('primaryRoutingExpansions', stats['routingExpansions'], probe_expansions, 3000000)]:
        need(integer(facts.get(key), 0, maximum) and facts[key] + extra == aggregate,
             'primary/probe work differs: ' + key)
    selected = stats['selectedRoutePolicy']
    need(selected is None or selected == 'P07_FULLER_TWO_LAYER', 'unknown selected route policy')
    if stats['outcome'] == 'SUCCESS':
        need(stats['selectedPlacementAttempt'] in successful_attempts and
             stats['selectedPlacementScore'] >= 0 and stats['selectedRouteQualityMilli'] >= 0,
             'selected attempt/metrics do not describe an attempted success')
    need(complete_text(stats) == policy_canonical(stats, facts.get('packingProbe')),
         'policy typed/canonical data differs')
    return stats


def validate(cases, summary):
    identity(summary)
    need(summary.get('scope') == SCOPE and summary.get('status') == 'PASS' and
         integer(summary.get('cases'), 2, 2) and integer(summary.get('authoredVariants'), 2, 2) and
         summary.get('variants') == VARIANTS, 'exact two-variant terminal summary differs')
    need(type(cases) is list and len(cases) == 2, 'exact two-case census required')
    for index, row in enumerate(cases):
        need(type(row) is dict and integer(row.get('protocolVersion'), 2, 2) and
             row.get('status') == 'PASS' and row.get('scope') == SCOPE and
             row.get('layoutQualification') is False and row.get('cleanupStatus') == 'PASS' and
             row.get('failureType') == row.get('cleanupFailureType') == 'none' and
             row.get('failure') == row.get('cleanupFailure') == '', 'row status/errors/cleanup differ')
        for key, low, high in [('operationMs', 0, 90000), ('cleanupMs', 0, 120000),
                               ('maxUnitMs', 0, 5000), ('workUnits', 1, 640)]:
            need(integer(row.get(key), low, high), 'invalid typed case budget: ' + key)
        facts = row.get('reportFacts')
        identity(facts)
        need(facts['runId'] == summary['runId'] and facts.get('variant') == VARIANTS[index] and
             facts.get('fixture') == 'AUTHORED_RB100' and integer(facts.get('components'), 100, 100) and
             integer(facts.get('pads'), 246, 246) and integer(facts.get('nets'), 59 + index, 59 + index) and
             facts.get('boardIdentityStatus') == facts.get('solverGraphIdentityStatus') == 'PASS',
             'actual graph/inventory/invocation identity differs')
        need(integer(facts.get('advanceCalls'), 1, row['workUnits']) and
             integer(facts.get('observerChecks'), facts['advanceCalls'], 2147483647),
             'actual advance/checkpoint census differs')
        limits = facts.get('limits')
        need(type(limits) is dict and all(type(limits.get(key)) is type(value) and limits[key] == value
                                        for key, value in LIMITS.items()), 'selected limits differ')
        stats = validate_stats(facts)
        output = facts.get('layoutOutput')
        need(type(output) is dict, 'missing terminal layout-output observation')
        keys = ['rawSegments', 'topTraceCount', 'bottomTraceCount', 'totalTraceCount',
                'holeCount', 'viaCount', 'platedHoleCount', 'nonPlatedHoleCount']
        need(all(integer(output.get(key), 0, 1000000) for key in keys) and output['viaCount'] <= 48 and
             output['topTraceCount'] + output['bottomTraceCount'] == output['totalTraceCount'] and
             output['viaCount'] + output['platedHoleCount'] + output['nonPlatedHoleCount'] == output['holeCount'],
             'observed geometry census differs')
        if facts.get('sessionTerminalStatus') == 'ROUTED':
            need(stats['outcome'] == 'SUCCESS' and stats['selectedRoutePolicy'] == 'P07_FULLER_TWO_LAYER' and
                 stats['twoLayerSuccesses'] > 0 and stats['selectedPlacementAttempt'] in stats['rankedPlacementAttempts'] and
                 facts.get('sessionException') is None and facts.get('policyFailure') is None and
                 output.get('layoutReturned') is True and output.get('zeroOutput') is False and
                 output['totalTraceCount'] > 0, 'route success facts differ')
            complete_text(output, 'fingerprint', 1024 * 1024)
            if facts.get('packingProbe') is not None:
                need(facts['packingProbe']['selectedGeometry'] ==
                     hashlib.sha256(output['fingerprint'].encode('utf-8')).hexdigest(),
                     'probe selected geometry does not bind actual output')
            outline = output.get('outline')
            need(type(outline) is dict and all(integer(outline.get(key), -1000000, 1000000)
                 for key in ['x', 'y']) and all(integer(outline.get(key), 1, 1000000)
                 for key in ['width', 'height']), 'missing actual routed outline')
        else:
            rejection = facts.get('sessionException')
            need(facts.get('sessionTerminalStatus') == 'EXHAUSTED' and stats['outcome'] == 'REJECTED' and
                 facts.get('packingProbe') is None and
                 stats['selectedRoutePolicy'] is None and stats['twoLayerSuccesses'] == 0 and
                 stats['selectedPlacementAttempt'] == stats['selectedPlacementScore'] ==
                 stats['selectedRouteQualityMilli'] == -1 and
                 type(rejection) is dict and rejection.get('type') == 'PcbRoutingRejectedException' and
                 rejection.get('kind') in {'PLACEMENT', 'ROUTING'} and rejection.get('exhausted') is True and
                 rejection.get('seed') == IDENTITY['placementSeed'] and
                 integer(rejection.get('attemptCount'), 1, 640) and
                 integer(rejection.get('attemptIndex'), 0, rejection['attemptCount'] - 1) and
                 type(facts.get('policyFailure')) is str and facts['policyFailure'] != '' and
                 type(rejection.get('message')) is str and rejection['message'].endswith(facts['policyFailure']) and
                 output.get('layoutReturned') is False and output.get('zeroOutput') is True and
                 output.get('fingerprint') is None and output.get('outline') is None and
                 all(output[key] == 0 for key in keys), 'typed exhaustion/output facts differ')


def extract(lines):
    markers = [(kind, strict_json(line[len(prefix):])) for line in lines
               for kind, prefix in [('case', PREFIX), ('summary', SUMMARY)] if line.startswith(prefix)]
    need([kind for kind, _ in markers] == ['case', 'case', 'summary'], 'two rows before one terminal summary required')
    cases, summary = [value for _, value in markers[:2]], markers[2][1]
    validate(cases, summary)
    return cases, summary


def negative_canaries(cases, summary):
    mutations = [lambda rows: rows.pop(), lambda rows: rows.__setitem__(1, copy.deepcopy(rows[0])),
        lambda rows: rows[0].__setitem__('operationMs', True),
        lambda rows: rows[0].__setitem__('cleanupStatus', 'FAIL'),
        lambda rows: rows[0].__setitem__('layoutQualification', True),
        lambda rows: rows[0]['reportFacts'].__setitem__('seed', 9007199254740993),
        lambda rows: rows[0]['reportFacts'].__setitem__('physicalIntentVersion', 4),
        lambda rows: rows[0]['reportFacts'].__setitem__('traceWidthProfile', 'UNIFORM_TRACE@1'),
        lambda rows: rows[0]['reportFacts'].__setitem__('placementDensityVersion', 2),
        lambda rows: rows[0]['reportFacts'].__setitem__('requiresTwoLayerRouting', False),
        lambda rows: rows[0]['reportFacts'].__setitem__('layoutVersion', 21),
        lambda rows: rows[0]['reportFacts'].__setitem__('dependencyEpoch', 'tsj-generation-dependencies-v22'),
        lambda rows: rows[0]['reportFacts'].__setitem__('layoutVersion', 24),
        lambda rows: rows[0]['reportFacts'].__setitem__('dependencyEpoch', 'tsj-generation-dependencies-v25'),
        lambda rows: rows[0]['reportFacts'].__setitem__('runId', '00000000-0000-0000-0000-000000000000'),
        lambda rows: rows[0]['reportFacts'].__setitem__('policyStatistics', None),
        lambda rows: rows[0]['reportFacts'].__setitem__('primaryRoutingExpansions', 3000001),
        lambda rows: rows[0]['reportFacts']['policyStatistics'].__setitem__('oneFaceAttempts', 1),
        lambda rows: rows[0]['reportFacts']['policyStatistics']['placementScores'].pop(),
        lambda rows: rows[0]['reportFacts']['policyStatistics']['routeOutcomes'].pop(),
        lambda rows: rows[0]['reportFacts']['policyStatistics'].__setitem__('canonical',
                     rows[0]['reportFacts']['policyStatistics']['canonical'][:-2]),
        lambda rows: rows[0]['reportFacts']['policyStatistics'].__setitem__('routeEligibilitySkipCount', 99),
        lambda rows: rows[0]['reportFacts']['policyStatistics'].__setitem__('routeEligiblePlacementAttemptCount', True)]
    count = 0
    for mutate in mutations:
        rows = copy.deepcopy(cases)
        mutate(rows)
        try:
            validate(rows, summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('malformed policy fixture accepted')
    for key, value in [('layoutVersion', 24),
                       ('dependencyEpoch', 'tsj-generation-dependencies-v25')]:
        altered_summary = copy.deepcopy(summary)
        altered_summary[key] = value
        try:
            validate(cases, altered_summary)
        except ValueError:
            count += 1
        else:
            raise ValueError('immediate-prior policy summary accepted: ' + key)
    lines = [PREFIX + json.dumps(row) for row in cases] + [SUMMARY + json.dumps(summary)]
    for altered in [[lines[-1]] + lines[:-1], lines + [lines[-1]]]:
        try:
            extract(altered)
        except ValueError:
            count += 1
        else:
            raise ValueError('stale/duplicate summary accepted')
    for value in ['{"status":"PASS","status":"FAIL"}', '{"x":NaN}']:
        try:
            strict_json(value)
        except ValueError:
            count += 1
        else:
            raise ValueError('invalid JSON accepted')
    return count


def routed_transport_canaries(cases, summary):
    """Exercise unobserved result branches without claiming actual routed geometry."""
    rows = copy.deepcopy(cases)
    row, facts = rows[0], rows[0]['reportFacts']
    row['phase'] = 'SYNTHETIC_TRANSPORT_ONLY'
    stats = facts['policyStatistics']
    attempts = [int(value.split('@', 1)[1].split('=', 1)[0]) for value in stats['routeOutcomes']]
    stats.update(outcome='SUCCESS', selectedRoutePolicy='P07_FULLER_TWO_LAYER',
                 twoLayerSuccesses=1, selectedPlacementAttempt=attempts[0],
                 selectedPlacementScore=0, selectedRouteQualityMilli=0)
    stats['routeOutcomes'] = ['P07_FULLER_TWO_LAYER@' + str(value) +
                             ('=SUCCESS' if index == 0 else '=REJECTED:SYNTHETIC_CONTROL')
                             for index, value in enumerate(attempts)]
    receipts = []
    for index, attempt in enumerate(attempts):
        successful = index == 0
        receipts.append({'attempt': attempt,
            'startedOrderings': 1 if successful else 3,
            'completedOrderings': 1 if successful else 3,
            'yieldedOrderings': 1 if successful else 0,
            'finalizedOrderings': 1 if successful else 0,
            'selectedOrdering': 0 if successful else -1,
            'expansions': 100 if successful else 10,
            'guidanceStarted': False,
            'comparisonComplete': False if successful else True,
            'termination': 'FIRST_SUCCESS_UNGUIDED' if successful else 'SYNTHETIC_NET:NO_PATH'})
    stats['orderingSearchReceipts'] = receipts
    stats['orderingSearchReceiptsCount'] = len(receipts)
    stats['routingOrders'] = sum(receipt['startedOrderings'] for receipt in receipts)
    stats['routingExpansions'] = sum(receipt['expansions'] for receipt in receipts)
    facts.update(sessionTerminalStatus='ROUTED', sessionException=None, policyFailure=None,
                 packingProbe=None, primaryRoutingOrders=stats['routingOrders'],
                 primaryRoutingExpansions=stats['routingExpansions'])
    fingerprint = 'layout=' + str(IDENTITY['layoutVersion']) + '|synthetic-transport-only'
    output = {'layoutReturned': True, 'zeroOutput': False, 'fingerprint': fingerprint,
              'outline': {'x': 0, 'y': 0, 'width': 100, 'height': 100}, 'rawSegments': 1,
              'topTraceCount': 1, 'bottomTraceCount': 0, 'totalTraceCount': 1,
              'holeCount': 0, 'viaCount': 0, 'platedHoleCount': 0, 'nonPlatedHoleCount': 0}
    set_text_metadata(output, 'fingerprint')
    facts['layoutOutput'] = output
    def refresh():
        stats['canonical'] = policy_canonical(stats, facts['packingProbe'])
        set_text_metadata(stats, 'canonical')
    refresh()
    validate(rows, summary)
    checked = 0
    legacy_empty_receipt = copy.deepcopy(rows)
    legacy_stats = legacy_empty_receipt[0]['reportFacts']['policyStatistics']
    legacy_stats['orderingSearchReceipts'] = []
    legacy_stats['orderingSearchReceiptsCount'] = 0
    legacy_stats['canonical'] = policy_canonical(
        legacy_stats, legacy_empty_receipt[0]['reportFacts']['packingProbe'])
    set_text_metadata(legacy_stats, 'canonical')
    need(';orderingSearchReceipts=' not in legacy_stats['canonical'],
         'empty legacy ordering vector was included in canonical text')
    validate(legacy_empty_receipt, summary)
    # This is transport coverage only; native selector controls prove the policy.
    # After a success the policy may explore another unique eligible candidate.
    reordered = copy.deepcopy(rows)
    reordered_stats = reordered[0]['reportFacts']['policyStatistics']
    if len(reordered_stats['routeOutcomes']) >= 3:
        reordered_stats['routeOutcomes'][1], reordered_stats['routeOutcomes'][2] = (
            reordered_stats['routeOutcomes'][2], reordered_stats['routeOutcomes'][1])
        if 'orderingSearchReceipts' in reordered_stats:
            reordered_stats['orderingSearchReceipts'][1], reordered_stats['orderingSearchReceipts'][2] = (
                reordered_stats['orderingSearchReceipts'][2], reordered_stats['orderingSearchReceipts'][1])
        reordered_stats['canonical'] = policy_canonical(reordered_stats, None)
        set_text_metadata(reordered_stats, 'canonical')
        validate(reordered, summary)
        for kind in ['before-success', 'duplicate', 'ineligible']:
            invalid = copy.deepcopy(reordered)
            invalid_stats = invalid[0]['reportFacts']['policyStatistics']
            outcomes = invalid_stats['routeOutcomes']
            if kind == 'before-success':
                outcomes[0], outcomes[1] = outcomes[1], outcomes[0]
            elif kind == 'duplicate':
                outcomes[2] = outcomes[1]
            else:
                excluded = next((value for value in range(6) if value not in
                                 invalid_stats['routeEligiblePlacementAttempts']), 6)
                outcomes[2] = 'P07_FULLER_TWO_LAYER@' + str(excluded) + '=REJECTED:SYNTHETIC_CONTROL'
            invalid_stats['canonical'] = policy_canonical(invalid_stats, None)
            set_text_metadata(invalid_stats, 'canonical')
            try:
                validate(invalid, summary)
            except ValueError:
                checked += 1
            else:
                raise ValueError('invalid post-success selection transport accepted')
    for key in ['selectedPlacementAttempt', 'selectedPlacementScore', 'selectedRouteQualityMilli']:
        mutated = copy.deepcopy(rows)
        data = mutated[0]['reportFacts']
        data['policyStatistics'][key] = -1
        data['policyStatistics']['canonical'] = policy_canonical(data['policyStatistics'], data['packingProbe'])
        set_text_metadata(data['policyStatistics'], 'canonical')
        try:
            validate(mutated, summary)
        except ValueError:
            checked += 1
        else:
            raise ValueError('contradictory routed selection accepted')
    selected_hash = hashlib.sha256(fingerprint.encode('utf-8')).hexdigest()
    probe = {'attempts': 0, 'orderings': 0, 'expansions': 0, 'millis': 0,
             'routePolicy': 'P07_FULLER_TWO_LAYER', 'disposition': 'NO_CHANGE',
             'baselineGeometry': selected_hash, 'selectedGeometry': selected_hash,
             'metricEvidence': {'state': 'NOT_MEASURED', 'baseline': None, 'candidate': None,
                                'unavailableReason': None}}
    def probe_refresh():
        probe['canonical'] = ('normal-packing@3;attempts=' + str(probe['attempts']) +
            ';orderings=' + str(probe['orderings']) + ';expansions=' + str(probe['expansions']) +
            ';millis=' + str(probe['millis']) + ';route=' + probe['routePolicy'] +
            ';disposition=' + probe['disposition'] + ';baseline=' + probe['baselineGeometry'] +
            ';selected=' + probe['selectedGeometry'])
        set_text_metadata(probe, 'canonical')
        facts['packingProbe'] = probe
        refresh()
    probe_refresh()
    validate(rows, summary)
    probe.update(attempts=1, orderings=1, expansions=10, millis=1, disposition='SELECTED',
                 baselineGeometry='0' * 64, metricEvidence={'state': 'COMPARED',
                 'baseline': {'area': 2, 'copper': 2, 'vias': 1, 'protectedVoid': 1},
                 'candidate': {'area': 1, 'copper': 1, 'vias': 0, 'protectedVoid': 0},
                 'unavailableReason': None})
    stats['routingOrders'] += 1
    stats['routingExpansions'] += 10
    probe_refresh()
    validate(rows, summary)
    for mutate in [lambda data: data['packingProbe']['metricEvidence']['candidate'].__setitem__('area', 3),
                   lambda data: (data['layoutOutput'].__setitem__('fingerprint', 'changed-output'),
                                 set_text_metadata(data['layoutOutput'], 'fingerprint')),
                   lambda data: data['packingProbe'].__setitem__('disposition', 'KEPT')]:
        altered = copy.deepcopy(rows)
        mutate(altered[0]['reportFacts'])
        try:
            validate(altered, summary)
        except ValueError:
            checked += 1
        else:
            raise ValueError('contradictory packing probe accepted')
    no_attempt = copy.deepcopy(cases)
    data = no_attempt[0]['reportFacts']
    data['policyStatistics'].update(routeAttempts=0, twoLayerAttempts=0, routeOutcomesCount=0,
                                    routeOutcomes=[], rankedPlacementCount=0, rankedPlacementAttempts=[],
                                    placementScoreCount=0, placementScores=[], placementRejections=6,
                                    routingOrders=0, routingExpansions=0,
                                    orderingSearchReceiptsCount=0, orderingSearchReceipts=[])
    data['primaryRoutingOrders'] = data['primaryRoutingExpansions'] = 0
    data['policyStatistics']['canonical'] = policy_canonical(data['policyStatistics'], data['packingProbe'])
    set_text_metadata(data['policyStatistics'], 'canonical')
    try:
        validate(no_attempt, summary)
    except ValueError:
        checked += 1
    else:
        raise ValueError('P07 observation without any P07 attempt accepted')
    return checked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--rejected-receipt', type=Path, action='append', default=[])
    args = parser.parse_args()
    need(not args.output.exists(), 'refusing existing output')
    raw = args.receipt.read_bytes()
    need(len(raw) <= 4 * 1024 * 1024, 'receipt exceeds transport bound')
    cases, summary = extract(raw.decode('utf-8-sig').splitlines())
    ordering_receipt_canaries = ordering_search_reader_canaries()
    negatives = (negative_canaries(cases, summary) + routed_transport_canaries(cases, summary) +
                 ordering_receipt_canaries)
    for path in args.rejected_receipt:
        try:
            extract(path.read_text(encoding='utf-8-sig').splitlines())
        except (ValueError, UnicodeError):
            negatives += 1
        else:
            raise ValueError('prior incompatible policy receipt accepted')
    result = {'protocol': 'rb100-p07-policy-reader-v2', 'status': 'PASS_OBSERVATION_ONLY',
              'scope': SCOPE, 'layoutQualification': False,
              'receiptSha256': hashlib.sha256(raw).hexdigest(),
              'readerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'helperSha256': {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                               for name in ['read_diagnostic.py', 'read_routing_diagnostic.py']},
              'negativeCanaries': negatives, 'orderingReceiptCanaries': ordering_receipt_canaries,
              'syntheticTransportFixtures': {'ROUTED': 'PASS_TRANSPORT_ONLY',
                  'POST_SUCCESS_ORDER': 'PASS_TRANSPORT_ONLY',
                  'LEGACY_EMPTY_ORDERING_RECEIPT_SUCCESS': 'PASS_TRANSPORT_ONLY',
                  'NO_CHANGE_PROBE': 'PASS_TRANSPORT_ONLY', 'SELECTED_PROBE': 'PASS_TRANSPORT_ONLY'},
              'summary': summary, 'cases': cases}
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print('PASS: actual two-case private P07 observation; negative canaries=' +
          str(result['negativeCanaries']) + '; physical qualification=false')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, UnicodeError, TypeError, KeyError, IndexError) as failure:
        print('FAIL:', failure)
        raise SystemExit(2)
