"""Generated self-contained full-D V2 pure semantic owner."""
from __future__ import annotations
PK_N_MAX_GROUPS = 640
PK_N_MAX_GROUP_NS = 5000000000
PK_N_MAX_GROUP_UNITS = 32768
PK_N_MAX_COPY_ENTRIES = 1048576
PK_N_COPY_CHUNK = 4096

class PK_N_ReaderError(ValueError):

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith('BF_') else 'BF_' + code

def PK_N_need(condition: bool, code: str) -> None:
    if not condition:
        raise PK_N_ReaderError(code)

def PK_N_exact_keys(value, keys, code: str) -> None:
    PK_N_need(type(value) is dict and set(value) == set(keys), code)

def PK_N_is_int(value, low: int=0, high: int=2 ** 63 - 1) -> bool:
    return type(value) is int and low <= value <= high

def PK_N__result_tree_minimum_units(root) -> int:
    stack = [(root, 0)]
    units = 0
    visited = 0

    def utf8_chunks(value):
        try:
            byte_count = len(value.encode('utf-8', errors='strict'))
        except UnicodeEncodeError:
            raise PK_N_ReaderError('RESULT_TREE_UTF8') from None
        return (byte_count + PK_N_COPY_CHUNK - 1) // PK_N_COPY_CHUNK
    while stack:
        value, depth = stack.pop()
        PK_N_need(depth <= 64, 'RESULT_TREE_DEPTH')
        visited += 1
        PK_N_need(visited <= 1000000, 'RESULT_TREE_SIZE')
        units += 1
        if type(value) is dict:
            for key, child in value.items():
                PK_N_need(type(key) is str, 'RESULT_TREE_KEY')
                visited += 1
                PK_N_need(visited <= 1000000, 'RESULT_TREE_SIZE')
                units += 1 + utf8_chunks(key)
                stack.append((child, depth + 1))
        elif type(value) is list:
            stack.extend(((child, depth + 1) for child in value))
        elif type(value) is str:
            units += utf8_chunks(value)
    return units

def PK_N_stable_cohort_order(net_ids, cohort_ids) -> list[str]:
    """Independently derive a stable requested order from observed net IDs."""
    PK_N_need(type(net_ids) is list and bool(net_ids) and (type(cohort_ids) is list) and bool(cohort_ids), 'ORDER_INPUT')
    PK_N_need(all((type(net) is str and bool(net) for net in net_ids)) and all((type(net) is str and bool(net) for net in cohort_ids)), 'ORDER_INPUT')
    seen = set()
    for net in net_ids:
        PK_N_need(net not in seen, 'ORDER_DUPLICATE')
        seen.add(net)
    cohort_seen = set()
    for net in cohort_ids:
        PK_N_need(net not in cohort_seen, 'ORDER_COHORT_DUPLICATE')
        cohort_seen.add(net)
        PK_N_need(net in seen, 'ORDER_COHORT_UNKNOWN')
    return list(cohort_ids) + [net for net in net_ids if net not in cohort_seen]

def PK_N__validate_terminal_failure(value, outcome, ordering, run_work, *, control):
    if value is None:
        PK_N_need(not (control and outcome['disposition'] == 'NO_PATH'), 'TERMINAL_FAILURE_REQUIRED')
        return None
    PK_N_exact_keys(value, ('failedNetId', 'searchIndex', 'searchPass', 'passBefore', 'passAfter', 'advanceCallOrdinal', 'ownerSearchCleared', 'searchReleased', 'ownerPromotedNetId', 'ownerReason'), 'TERMINAL_FAILURE_SCHEMA')
    failed = value['failedNetId']
    requested = ordering['requestedNetIds']
    traversed = ordering['traversedNetIds']
    advance_calls = run_work['advanceCalls']
    advance_ordinal = value['advanceCallOrdinal']
    PK_N_need(type(failed) is str and bool(failed) and PK_N_is_int(value['searchPass'], 2, 2) and PK_N_is_int(value['passBefore'], 2, 2) and PK_N_is_int(value['passAfter'], 3, 3) and PK_N_is_int(value['searchIndex'], 1, len(requested)) and PK_N_is_int(advance_calls, 2, 2000000) and PK_N_is_int(advance_ordinal, 1, advance_calls - 1) and (advance_ordinal + 1 == advance_calls) and (value['ownerSearchCleared'] is True) and (value['searchReleased'] is True) and (value['ownerPromotedNetId'] == failed) and (type(value['ownerReason']) is str) and bool(value['ownerReason']) and (requested[value['searchIndex'] - 1] == failed) and (traversed.count(failed) == 1), 'TERMINAL_FAILURE_BINDING')
    reason = outcome['reason']
    suffix = reason.rsplit(':', 1)[-1] if type(reason) is str else None
    reason_net = reason.rsplit(':', 1)[0] if type(reason) is str and ':' in reason else None
    allowed = {'NO_PATH': {'NO_PATH'}, 'CAP_REJECTED': {'GRID_LIMIT', 'TOTAL_SEARCH_LIMIT', 'BRANCH_SEARCH_LIMIT'}, 'QUALITY_REJECTED': {'QUALITY_LIMIT', 'BRANCH_QUALITY_LIMIT'}}
    PK_N_need(value['ownerReason'] == reason and suffix in allowed.get(outcome['disposition'], set()) and (reason_net is None or reason_net == failed) and (suffix != 'GRID_LIMIT' or reason_net is None) and (outcome['disposition'] != 'NO_PATH' or reason_net == failed), 'TERMINAL_FAILURE_REASON')
    return value

def PK_N__validate_run(run, kind: str, captured: dict, cohort_ids) -> dict:
    PK_N_exact_keys(run, ('kind', 'initial', 'ordering', 'outcome', 'terminalFailure', 'finalizers', 'layout', 'work'), 'FRESH_RUN_SCHEMA')
    PK_N_need(run['kind'] == kind, 'FRESH_RUN_KIND')
    owner = captured['owner']
    initial = run['initial']
    PK_N_exact_keys(initial, ('pass', 'passes', 'expanded', 'maximumExpansions', 'maximumVias', 'priorPromotedNetId', 'policy', 'viaCost', 'policyTransitions', 'policyVias', 'branchOrderStrategy', 'netOrderStrategy', 'roleWidths', 'capturedOrderingStream', 'sessionOrderingStream', 'instrumentationModeDifference', 'routingRelevantSettingsMatch', 'traceCount', 'holeCount'), 'FRESH_INITIAL_SCHEMA')
    int_expectations = (('pass', 2), ('passes', 0), ('expanded', 0), ('maximumExpansions', owner['maximumExpansions']), ('maximumVias', owner['maximumVias']), ('viaCost', owner['viaCost']), ('policyTransitions', owner['policyTransitions']), ('policyVias', owner['policyVias']), ('traceCount', 0), ('holeCount', 0))
    PK_N_need(all((PK_N_is_int(initial[name], expected, expected) for name, expected in int_expectations)) and initial['priorPromotedNetId'] == captured['priorPromotedNetId'] and (initial['policy'] == owner['policy']) and (initial['branchOrderStrategy'] == owner['branchOrderStrategy']) and (initial['netOrderStrategy'] == owner['netOrderStrategy']) and (initial['roleWidths'] is owner['roleWidths']) and (initial['capturedOrderingStream'] is owner['orderingStream']) and (initial['sessionOrderingStream'] is False) and (initial['instrumentationModeDifference'] is owner['orderingStream']) and (initial['routingRelevantSettingsMatch'] is True), 'FRESH_INITIAL_BINDING')
    ordering = run['ordering']
    PK_N_exact_keys(ordering, ('capturedNetIds', 'computedNetIds', 'requestedCohortNetIds', 'requestedNetIds', 'traversedNetIds', 'orderMatchesCapture', 'searchCount'), 'FRESH_ORDERING_SCHEMA')
    captured_ids = captured['netIds']
    expected = PK_N_stable_cohort_order(captured_ids, cohort_ids)
    traversed = ordering['traversedNetIds']
    PK_N_need(type(traversed) is list and all((type(net) is str for net in traversed)) and (ordering['capturedNetIds'] == captured_ids) and (ordering['computedNetIds'] == captured_ids) and (ordering['requestedCohortNetIds'] == cohort_ids) and (ordering['requestedNetIds'] == expected) and (ordering['orderMatchesCapture'] is True) and PK_N_is_int(ordering['searchCount'], 1, 1) and (len(traversed) <= len(expected)) and (traversed == expected[:len(traversed)]) and (len(set(traversed)) == len(traversed)), 'FRESH_ORDERING_BINDING')
    outcome = run['outcome']
    PK_N_exact_keys(outcome, ('disposition', 'reason', 'complete', 'ownerComplete', 'pass', 'passes', 'expanded', 'maximumExpansions', 'errorClass', 'errorMessage'), 'FRESH_OUTCOME_SCHEMA')
    disposition, reason = (outcome['disposition'], outcome['reason'])
    PK_N_need(disposition in ('FOUND', 'NO_PATH', 'CAP_REJECTED', 'QUALITY_REJECTED') and (reason is None or (type(reason) is str and bool(reason))) and (type(outcome['complete']) is bool) and (type(outcome['ownerComplete']) is bool) and (outcome['ownerComplete'] is True) and PK_N_is_int(outcome['pass'], 2, 3) and PK_N_is_int(outcome['passes'], 1, 1) and PK_N_is_int(outcome['expanded'], 0, owner['maximumExpansions']) and PK_N_is_int(outcome['maximumExpansions'], owner['maximumExpansions'], owner['maximumExpansions']) and (outcome['errorClass'] is None) and (outcome['errorMessage'] is None), 'FRESH_OUTCOME_BINDING')
    if disposition == 'FOUND':
        PK_N_need(reason == 'SUCCESS' and outcome['complete'] is True and (outcome['pass'] == 2) and (traversed == expected), 'FOUND_OUTCOME')
    else:
        PK_N_need(type(reason) is str and bool(reason) and (outcome['complete'] is False) and (outcome['pass'] == 3), 'NONFOUND_OUTCOME')
        suffix = reason.rsplit(':', 1)[-1]
        reason_net = reason.rsplit(':', 1)[0] if ':' in reason else None
        allowed_suffixes = {'NO_PATH': {'NO_PATH'}, 'CAP_REJECTED': {'GRID_LIMIT', 'TOTAL_SEARCH_LIMIT', 'BRANCH_SEARCH_LIMIT'}, 'QUALITY_REJECTED': {'QUALITY_LIMIT', 'BRANCH_QUALITY_LIMIT'}}
        PK_N_need(suffix in allowed_suffixes[disposition] and (reason_net is None or reason_net in captured['netIds']) and (suffix != 'GRID_LIMIT' or reason_net is None), 'OUTCOME_REASON')
    PK_N_need((disposition == 'FOUND') == (run['layout'] is not None), 'FRESH_LAYOUT_DISPOSITION')
    finalizers = run['finalizers']
    PK_N_exact_keys(finalizers, ('routingGeometry', 'allNetConnectivity', 'twoLayerRules', 'routeQuality', 'viaCap'), 'FINALIZERS_SCHEMA')
    if disposition == 'FOUND':
        PK_N_need(all((finalizers[field] == 'PASS' for field in finalizers)), 'FOUND_FINALIZERS')
    elif disposition == 'QUALITY_REJECTED' and reason == 'QUALITY_LIMIT':
        PK_N_need(finalizers == {'routingGeometry': 'PASS', 'allNetConnectivity': 'PASS', 'twoLayerRules': 'PASS', 'routeQuality': 'REJECTED', 'viaCap': 'NOT_RUN'}, 'QUALITY_FINALIZERS')
    else:
        PK_N_need(all((finalizers[field] == 'NOT_RUN' for field in finalizers)), 'NONFOUND_FINALIZERS')
    work = run['work']
    PK_N_exact_keys(work, ('advanceCalls', 'ownerUnits', 'wrapperUnits', 'copyEntries', 'copyChunks', 'groups', 'maxGroupNanos', 'maximumUnitsPerGroup', 'failureObservationUnits', 'terminalEvidenceUnits'), 'RUN_WORK_SCHEMA')
    PK_N_need(PK_N_is_int(work['advanceCalls'], 1, 2000000) and PK_N_is_int(work['ownerUnits'], 0, PK_N_MAX_GROUP_UNITS * PK_N_MAX_GROUPS) and PK_N_is_int(work['wrapperUnits'], 0, PK_N_MAX_GROUP_UNITS * PK_N_MAX_GROUPS) and PK_N_is_int(work['failureObservationUnits'], work['advanceCalls'], work['advanceCalls']) and PK_N_is_int(work['terminalEvidenceUnits'], 0, PK_N_MAX_GROUP_UNITS * PK_N_MAX_GROUPS) and (work['wrapperUnits'] >= work['advanceCalls'] + work['failureObservationUnits'] + work['terminalEvidenceUnits']) and PK_N_is_int(work['copyEntries'], 0, PK_N_MAX_COPY_ENTRIES) and PK_N_is_int(work['copyChunks'], 0, PK_N_MAX_COPY_ENTRIES) and PK_N_is_int(work['groups'], 1, PK_N_MAX_GROUPS) and PK_N_is_int(work['maxGroupNanos'], 1, PK_N_MAX_GROUP_NS) and PK_N_is_int(work['maximumUnitsPerGroup'], 1, PK_N_MAX_GROUP_UNITS) and (work['copyChunks'] <= work['copyEntries']) and (work['copyEntries'] == 0 or work['copyChunks'] >= (work['copyEntries'] + PK_N_COPY_CHUNK - 1) // PK_N_COPY_CHUNK), 'RUN_WORK_BOUNDS')
    PK_N_need(traversed or disposition == 'FOUND', 'EMPTY_TRAVERSAL')
    terminal = PK_N__validate_terminal_failure(run['terminalFailure'], outcome, ordering, work, control=kind == 'OBSERVED_FAILURE_CONTROL')
    expected_terminal_units = 0 if terminal is None else 11 + len(traversed) + (1 if kind == 'OBSERVED_FAILURE_CONTROL' and disposition == 'NO_PATH' else 0)
    PK_N_need(work['terminalEvidenceUnits'] == expected_terminal_units, 'TERMINAL_EVIDENCE_ACCOUNTING')
    return {'expectedOrder': expected, 'ordering': ordering, 'outcome': outcome, 'terminalFailure': terminal, 'work': work, 'layout': run['layout']}
import base64 as PK_P07_base64
from collections import Counter as PK_P07_Counter, deque as PK_P07_deque
import hashlib as PK_P07_hashlib
import heapq as PK_P07_heapq
import json as PK_P07_json
import re as PK_P07_re
PK_P07_SCHEMA = 'p07-actual-branch-order-study-v6'
PK_P07_ARMS = (('PLAIN_ENTRY_RECEIPT_CONTROL', 'PLAIN_BEGIN', 'CANONICAL_NEAREST', 'CANONICAL', False, False), ('OBSERVED_DEFAULT_NEAREST', 'BOUNDARY_CONVENIENCE', 'CANONICAL_NEAREST', 'CANONICAL', True, False), ('OBSERVED_EXPLICIT_NEAREST', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST', 'CANONICAL', True, False), ('SECOND_ORDER_MINIMUM_PAIR', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST', 'SECOND_ORDER_MINIMUM_PAIR', True, False), ('MINIMUM_PAIR_PUBLISHER_CAPTURE', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST', 'SECOND_ORDER_MINIMUM_PAIR', True, True))
PK_P07_LIMITS = dict(operationNanos=90000000000, groups=640, groupNanos=5000000000, p07Steps=32768, receiptBytes=8 * 1024 * 1024, lineBytes=65536, padRows=1024, prefixBytes=1024 * 1024, viaCost=60, holeCap=48, transitionCap=4, orderings=3, requestExpansions=1000000, branchExpansions=100000, cells=250000, pathNodes=4096, snapshotEntries=1048576, publisherPayloadBytes=2 * 1024 * 1024, publisherChunkBytes=4096)
PK_P07_SNAP_INTS = 'pass startX startY rootX rootY gridWidth gridHeight goalsTop goalsBottom prefixViaCount reachedPadCount initialManhattanDistance minimumExploredManhattanDistance branchExpanded totalExpanded expandedTop expandedBottom frontierCount bestStateCount relaxedBoundWorkUnits relaxedBoundOrdinaryProbes relaxedBoundViaProbes relaxedBoundRefreshes'.split()
PK_P07_SNAP_BOOLS = 'relaxedBoundStarted relaxedBoundComplete relaxedBoundObservationChecked relaxedBoundLiveLayoutStable relaxedBoundRouterStateStable relaxedBoundRouterEffectsStable'.split()
PK_P07_SNAP_STRINGS = ['netId', 'startPadId', 'rootPadId', 'terminalReason']
PK_P07_RECEIPT_FIELDS = 'attempt startedOrderings completedOrderings yieldedOrderings finalizedOrderings selectedOrdering expansions guidanceStarted comparisonComplete termination'.split()
PK_P07_GROUP_FIELDS = 'record operation ordinal phase elapsedBeforeOwnLineNanos generatorAdvances p07Slices p07Steps diagnosticCloneSteps accounting observerChecks callbacks censusRows netOrderRows hashInputBytes serializedBeforeOwnLineBytes publisherChunkRows publisherResultRows diagnosticCopiedEntries graphChunkRows graphResultRows graphCursorSteps diagnosticSerializedBytes diagnosticHashBytes diagnosticUnits'.split()
PK_P07_NET_ORDER_FIELDS = 'record operation group pass strategy promotedNet completedOrderings yieldedOrderings prefixSha256 prefixBytes netIds'.split()
PK_P07_PUB_CHUNK_FIELDS = 'record operation group targetId index offset bytes data'.split()
PK_P07_PUB_RESULT_FIELDS = 'record operation group targetId payloadBytes payloadSha256 chunkCount'.split()
PK_P07_GRAPH_CHUNK_FIELDS = 'record operation group targetId index offset bytes data'.split()
PK_P07_GRAPH_RESULT_FIELDS = 'record operation group targetId payloadBytes payloadSha256 chunkCount'.split()
PK_P07_PUBLISHER_TARGET_SCHEMA = 'p07-publisher-target-v1'
PK_P07_PUBLISHER_TARGET_ID = 'op4/pass1/target0'
PK_P07_GRAPH_TARGET_SCHEMA = 'p07-outf-rejected-publisher-graph-v1'
PK_P07_GRAPH_TARGET_ID = 'op4/pass1/target0'
PK_P07_GRAPH_BODY_SCHEMA = 'p07-zero-via-graph-r5'
PK_P07_DIAGNOSTIC_CLONE_FIELDS = 'record operation ordinal group pass netId startPadId rootPadId faceIndex prefixViaCount ownerSteps'.split()
PK_P07_DIAGNOSTIC_BOUND_PROOFS = 'relaxedBoundObservationChecked relaxedBoundLiveLayoutStable relaxedBoundRouterStateStable relaxedBoundRouterEffectsStable'.split()
PK_P07_TOKEN = PK_P07_re.compile('[-+A-Za-z0-9_.:]{1,128}\\Z')
PK_P07_HEX = PK_P07_re.compile('[0-9a-f]{64}\\Z')

def PK_P07_need(condition, code):
    if not condition:
        raise ValueError(code)

def PK_P07_keys(value, names, code):
    PK_P07_need(type(value) is dict and set(value) == set(names), code)

def PK_P07_same_json_value(left, right):
    """Compare JSON values without Python's bool/int equality coercion."""
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return set(left) == set(right) and all((PK_P07_same_json_value(left[name], right[name]) for name in left))
    if type(left) is list:
        return len(left) == len(right) and all((PK_P07_same_json_value(a, b) for a, b in zip(left, right)))
    return left == right

def PK_P07_integer(value, lo=0, hi=2 ** 63 - 1):
    return type(value) is int and lo <= value <= hi

def PK_P07_sha(raw):
    return PK_P07_hashlib.sha256(raw).hexdigest()

def PK_P07_digest(value):
    return type(value) is str and PK_P07_HEX.fullmatch(value) is not None

def PK_P07_token(value):
    return type(value) is str and PK_P07_TOKEN.fullmatch(value) is not None

def PK_P07_strict_json(raw):
    PK_P07_need(type(raw) is bytes and (not raw.startswith(b'\xef\xbb\xbf')), 'JSON_BOM')

    def pairs(rows):
        out = {}
        for k, v in rows:
            PK_P07_need(k not in out, 'JSON_DUPLICATE_KEY')
            out[k] = v
        return out

    def reject(_):
        raise ValueError('JSON_NONINTEGER')

    def number(text):
        PK_P07_need(text != '-0', 'JSON_NEGATIVE_ZERO')
        return int(text)
    value = PK_P07_json.loads(raw.decode('utf-8', errors='strict'), object_pairs_hook=pairs, parse_int=number, parse_float=reject, parse_constant=reject)

    def strings(v):
        if type(v) is str:
            PK_P07_need(not any((55296 <= ord(c) <= 57343 for c in v)), 'JSON_SURROGATE')
        elif type(v) is dict:
            for k, item in v.items():
                strings(k)
                strings(item)
        elif type(v) is list:
            for item in v:
                strings(item)
    strings(value)
    return value

def PK_P07_pad_bytes(pads):
    return ''.join(('\t'.join((str(row[k]) for k in ('padId', 'netId', 'componentId', 'x', 'y', 'role'))) + '\n' for row in pads)).encode('utf-8')

def PK_P07_audit_net_order(row, pads, branches, arm):
    """Rebuild both candidate vectors from PAD rows; producer metrics are ignored."""
    by_net = {}
    for pad in pads:
        by_net.setdefault(pad['netId'], []).append(pad)
    net_ids = sorted(by_net)
    canonical = sorted(net_ids, key=lambda net: (-len(by_net[net]), net))
    pair_metrics = {}
    witnesses = {}
    for net in net_ids:
        net_pads = sorted(by_net[net], key=lambda pad: pad['padId'])
        best = None
        for left_index, left in enumerate(net_pads):
            for right in net_pads[left_index + 1:]:
                distance = abs(left['x'] - right['x']) + abs(left['y'] - right['y'])
                candidate = (distance, left['padId'], right['padId'])
                if best is None or candidate < best:
                    best = candidate
        if best is not None:
            pair_metrics[net] = best[0]
            witnesses[net] = (best[1], best[2])
    minimum_pair = sorted(pair_metrics, key=lambda net: (pair_metrics[net], net))
    minimum_pair.extend((net for net in net_ids if net not in pair_metrics))
    base_order = canonical if arm in (1, 2) else minimum_pair
    expected = list(base_order)
    if row['promotedNet'] is not None:
        PK_P07_need(row['promotedNet'] in expected, 'NET_ORDER_PROMOTION_CENSUS')
        expected.remove(row['promotedNet'])
        expected.insert(0, row['promotedNet'])
    PK_P07_need(row['netIds'] == expected, 'NET_ORDER_VECTOR')
    callback_nets = []
    for branch in branches:
        snapshot = branch['snapshot']
        if snapshot['pass'] == 1 and (not callback_nets or callback_nets[-1] != snapshot['netId']):
            callback_nets.append(snapshot['netId'])
    cursor = 0
    for callback_net in callback_nets:
        try:
            cursor = row['netIds'].index(callback_net, cursor) + 1
        except ValueError:
            raise ValueError('NET_ORDER_BRANCH_SUBSEQUENCE')
    return dict(strategy=row['strategy'], canonicalNetIds=canonical, minimumPairNetIds=minimum_pair, expectedNetIds=expected, baseNetIds=base_order, minimumPairs=[dict(netId=net, distance=pair_metrics[net], witnessPadIds=list(witnesses[net])) for net in sorted(pair_metrics)], singletonNetIds=[net for net in net_ids if net not in pair_metrics], observedNetIds=list(row['netIds']))

def PK_P07_validate_net_order_promotion(row, branches, pad_count, net_count):
    """Bind promotion to complete pass-zero branch observations."""
    pass_zero = [branch['snapshot'] for branch in branches if branch['snapshot']['pass'] == 0]
    PK_P07_need(pass_zero and row['completedOrderings'] == 1, 'NET_ORDER_PRIOR_COUNTERS')
    if row['yieldedOrderings'] == 1:
        PK_P07_need(row['promotedNet'] is None and len(pass_zero) == pad_count - net_count and all((snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero)), 'NET_ORDER_YIELDED_PROMOTION')
    else:
        last = pass_zero[-1]
        PK_P07_need(not all((snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero)), 'NET_ORDER_AMBIGUOUS_PROMOTION')
        PK_P07_need(all((snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero[:-1])) and last['terminalReason'] in ('NO_PATH', 'BRANCH_SEARCH_LIMIT') and (row['promotedNet'] == last['netId']), 'NET_ORDER_FAILED_PROMOTION')

def PK_P07_validate_receipt(r):
    PK_P07_keys(r, PK_P07_RECEIPT_FIELDS, 'RECEIPT_SCHEMA')
    for name in PK_P07_RECEIPT_FIELDS[:7]:
        PK_P07_need(PK_P07_integer(r[name], -1 if name == 'selectedOrdering' else 0), 'RECEIPT_NUMBER')
    PK_P07_need(PK_P07_integer(r['attempt'], 0, 5) and PK_P07_integer(r['startedOrderings'], 1, 3) and PK_P07_integer(r['completedOrderings'], 1, r['startedOrderings']) and PK_P07_integer(r['yieldedOrderings'], 0, r['completedOrderings']) and PK_P07_integer(r['finalizedOrderings'], 0, r['yieldedOrderings']) and PK_P07_integer(r['selectedOrdering'], -1, r['startedOrderings'] - 1) and PK_P07_integer(r['expansions'], 0, 1000000), 'RECEIPT_BOUNDS')
    PK_P07_need(type(r['guidanceStarted']) is bool and type(r['comparisonComplete']) is bool and (type(r['termination']) is str) and (0 < len(r['termination'].encode()) <= 256), 'RECEIPT_TERMINAL')
    PK_P07_need(r['selectedOrdering'] < 0 or r['finalizedOrderings'] > 0, 'RECEIPT_WINNER')
    if r['comparisonComplete']:
        PK_P07_need(r['startedOrderings'] == r['completedOrderings'] == 3 and r['termination'] != 'TOTAL_SEARCH_LIMIT', 'RECEIPT_COMPARISON')
    if r['termination'] == 'ORDERINGS_COMPLETE':
        PK_P07_need(r['comparisonComplete'] and r['yieldedOrderings'] > 0, 'RECEIPT_COMPLETE')
    if r['termination'] == 'FIRST_SUCCESS_UNGUIDED':
        PK_P07_need(not r['guidanceStarted'] and (not r['comparisonComplete']) and (r['yieldedOrderings'] == 1), 'RECEIPT_FIRST_SUCCESS')

def PK_P07_utf16_length(value):
    return len(value.encode('utf-16-le')) // 2

def PK_P07_java_field(value):
    if value is None:
        return '-1:'
    return str(PK_P07_utf16_length(value)) + ':' + value

def PK_P07_java_text(value):
    return 'null' if value is None else value

def PK_P07_java_lexical_key(value):
    raw = value.encode('utf-16-le')
    return tuple((raw[i] | raw[i + 1] << 8 for i in range(0, len(raw), 2)))

def PK_P07__geometry_id(value):
    return type(value) is str and 0 < len(value) <= 4096 and (not any((ord(ch) < 32 or ord(ch) == 127 or 55296 <= ord(ch) <= 57343 for ch in value)))

def PK_P07__point_coordinate(value):
    return PK_P07_integer(value, -100000000, 100000000)

def PK_P07__freeze(value):
    if type(value) is list:
        return tuple((PK_P07__freeze(item) for item in value))
    if type(value) is dict:
        return tuple(sorted(((key, PK_P07__freeze(item)) for key, item in value.items())))
    return value

def PK_P07__geometry_rows(snapshot):
    PK_P07_keys(snapshot, ('staticPrefix', 'traces', 'holes', 'geometrySha256', 'geometryBytes'), 'PUB_GEOMETRY_SCHEMA')
    prefix = snapshot['staticPrefix']
    PK_P07_need(type(prefix) is str and (not any((55296 <= ord(ch) <= 57343 for ch in prefix))), 'PUB_GEOMETRY_PREFIX')
    traces = snapshot['traces']
    holes = snapshot['holes']
    PK_P07_need(type(traces) is list and type(holes) is list and (len(holes) <= PK_P07_LIMITS['holeCap']), 'PUB_GEOMETRY_ROWS')
    trace_ids = set()
    for row in traces:
        PK_P07_need(type(row) is list and len(row) == 9, 'PUB_TRACE_ROW')
        source_id, net_id, start_id, end_id, layer, exposure, width, xs, ys = row
        PK_P07_need(source_id is None or PK_P07__geometry_id(source_id), 'PUB_TRACE_ID')
        if source_id is not None:
            PK_P07_need(source_id not in trace_ids, 'PUB_TRACE_ID_DUPLICATE')
            trace_ids.add(source_id)
        PK_P07_need(PK_P07_token(net_id) and (start_id is None or PK_P07_token(start_id)) and (end_id is None or PK_P07_token(end_id)), 'PUB_TRACE_ENDPOINT')
        PK_P07_need(PK_P07_integer(layer, 0, 1) and exposure in ('EXPOSED', 'COVERED') and PK_P07_integer(width, 9, 63) and (width % 2 == 1), 'PUB_TRACE_RULES')
        PK_P07_need(type(xs) is list and type(ys) is list and (2 <= len(xs) == len(ys)) and (len(xs) <= PK_P07_LIMITS['pathNodes']) and all((PK_P07__point_coordinate(x) for x in xs)) and all((PK_P07__point_coordinate(y) for y in ys)), 'PUB_TRACE_POINTS')
    hole_ids = set()
    for row in holes:
        PK_P07_need(type(row) is list and len(row) == 8, 'PUB_HOLE_ROW')
        hole_id, kind, net_id, x, y, drill, land, exposure = row
        PK_P07_need(PK_P07__geometry_id(hole_id) and hole_id not in hole_ids, 'PUB_HOLE_ID')
        hole_ids.add(hole_id)
        PK_P07_need(kind in ('VIA', 'PLATED', 'NON_PLATED') and exposure in ('EXPOSED', 'COVERED') and PK_P07__point_coordinate(x) and PK_P07__point_coordinate(y) and PK_P07_integer(drill, 1, 100000000) and PK_P07_integer(land, 1, 100000000), 'PUB_HOLE_RULES')
        if kind == 'NON_PLATED':
            PK_P07_need(net_id is None and land == drill, 'PUB_NPTH_RULES')
        else:
            PK_P07_need(PK_P07_token(net_id) and land > drill, 'PUB_PLATED_HOLE_RULES')
    fingerprint = PK_P07_geometry_fingerprint(prefix, traces, holes)
    raw = fingerprint.encode('utf-8')
    PK_P07_need(PK_P07_digest(snapshot['geometrySha256']) and snapshot['geometrySha256'] == PK_P07_sha(raw) and PK_P07_integer(snapshot['geometryBytes'], 1, PK_P07_LIMITS['prefixBytes']) and (snapshot['geometryBytes'] == len(raw)), 'PUB_GEOMETRY_FINGERPRINT')
    return (fingerprint, raw)

def PK_P07_geometry_fingerprint(static_prefix, traces, holes):
    """Rebuild PcbBoardLayout's native trace/hole suffix using Java ordering."""

    def trace_key(row):
        source_id, net_id, start_id, end_id, layer, _exposure, width, _xs, _ys = row
        return (PK_P07_java_lexical_key(net_id), PK_P07_java_lexical_key(PK_P07_java_text(start_id)), PK_P07_java_lexical_key(PK_P07_java_text(end_id)), layer, width, PK_P07_java_lexical_key(PK_P07_java_text(source_id)))
    out = [static_prefix]
    for row in sorted(traces, key=trace_key):
        source_id, net_id, start_id, end_id, layer, exposure, width, xs, ys = row
        layer_name = 'TOP' if layer == 0 else 'BOTTOM'
        out.append('T:' + PK_P07_java_field(source_id) + layer_name + ':' + exposure + ':' + net_id + ':' + PK_P07_java_text(start_id) + '-' + PK_P07_java_text(end_id) + '@')
        if width != 9:
            out.append('W' + str(width) + ':')
        out.extend((str(x) + ',' + str(y) + ';' for x, y in zip(xs, ys)))
    for row in sorted(holes, key=lambda item: PK_P07_java_lexical_key(item[0])):
        hole_id, kind, net_id, x, y, drill, land, exposure = row
        out.append('H:' + PK_P07_java_field(hole_id) + kind + PK_P07_java_field(net_id) + str(x) + ',' + str(y) + ',' + str(drill) + ',' + str(land) + ',' + exposure)
    return ''.join(out)

def PK_P07__compact_piece(nodes):
    compact = []
    for node in nodes:
        point = (node[2], node[3])
        if len(compact) >= 2:
            a, b = (compact[-2], compact[-1])
            if a[0] == b[0] == point[0] or a[1] == b[1] == point[1]:
                compact.pop()
        compact.append(point)
    return compact

def PK_P07__prefix_goal_supported(target, pads_by_id, endpoint_ids, x, y, layer, net_id):
    for pad_id in endpoint_ids:
        if (pads_by_id[pad_id]['x'], pads_by_id[pad_id]['y']) == (x, y):
            return True
    for trace in target['before']['traces']:
        _source, trace_net, _start, _end, trace_layer, _exposure, _width, xs, ys = trace
        if trace_net != net_id or trace_layer != layer:
            continue
        for index in range(len(xs) - 1):
            x1, y1, x2, y2 = (xs[index], ys[index], xs[index + 1], ys[index + 1])
            if x1 == x2 == x and min(y1, y2) <= y <= max(y1, y2) and ((y - min(y1, y2)) % target['grid']['pitch'] == 0):
                return True
            if y1 == y2 == y and min(x1, x2) <= x <= max(x1, x2) and ((x - min(x1, x2)) % target['grid']['pitch'] == 0):
                return True
    for hole in target['before']['holes']:
        _hole_id, kind, hole_net, hole_x, hole_y, _drill, _land, _exposure = hole
        if kind in ('VIA', 'PLATED') and hole_net == net_id and ((hole_x, hole_y) == (x, y)):
            return True
    return False

def PK_P07__publisher_geometry_delta(target, pads_by_id, net_roles, branches):
    branch = target['branch']
    nodes = target['path']['nodes']
    grid = target['grid']
    terminal_id = branch['foundTerminalPadId']
    net_id = branch['netId']
    prior_in_pass = [row for row in branches if row['snapshot']['pass'] == target['context']['pass'] and row['ordinal'] <= branch['ordinal']]
    PK_P07_need(prior_in_pass and prior_in_pass[-1]['ordinal'] == branch['ordinal'], 'PUB_PASS_BRANCH_ORDINAL')
    branch_id = len(prior_in_pass)
    widths = {'HIGH_CURRENT': 13, 'SUPPLY': 11, 'RETURN': 11, 'CONTROL': 9, 'SIGNAL': 9}
    PK_P07_need(net_id in net_roles and net_roles[net_id] in widths, 'PUB_NET_ROLE_WIDTH')
    width = widths[net_roles[net_id]]
    before_holes = target['before']['holes']
    known_holes = list(before_holes)
    expected_traces = []
    expected_holes = []
    expected_segments = 0
    piece_number = 0
    first = 0
    while first < len(nodes):
        end = first + 1
        while end < len(nodes) and nodes[end][1] == nodes[end - 1][1]:
            end += 1
        last = end - 1
        piece_nodes = nodes[first:end]
        if len(piece_nodes) > 1:
            points = PK_P07__compact_piece(piece_nodes)
            endpoint_id = None
            if last == len(nodes) - 1:
                if terminal_id is not None:
                    endpoint_id = terminal_id
                elif (nodes[last][2], nodes[last][3]) == (pads_by_id[branch['rootPadId']]['x'], pads_by_id[branch['rootPadId']]['y']):
                    endpoint_id = branch['rootPadId']
            expected_traces.append(['p07/{}/{}/{}'.format(net_id, branch_id, piece_number), net_id, branch['startPadId'] if first == 0 else None, endpoint_id, piece_nodes[0][1], 'EXPOSED', width, [point[0] for point in points], [point[1] for point in points]])
            expected_segments += len(piece_nodes) - 1
            piece_number += 1
        if end < len(nodes):
            via_x, via_y = (nodes[last][2], nodes[last][3])
            at_xy = [hole for hole in known_holes if hole[3:5] == [via_x, via_y]]
            if at_xy:
                PK_P07_need(all((hole[1] in ('VIA', 'PLATED') and hole[2] == net_id for hole in at_xy)), 'PUB_VIA_FOREIGN_OR_NPTH')
            else:
                via = ['p07-via/{}/{}/{}'.format(net_id, branch_id, piece_number), 'VIA', net_id, via_x, via_y, 2, 6, 'EXPOSED']
                expected_holes.append(via)
                known_holes.append(via)
        first = end
    PK_P07_need(len(before_holes) + len(expected_holes) <= PK_P07_LIMITS['holeCap'] and len(before_holes) + len(expected_holes) <= grid['maximumVias'], 'PUB_VIA_CAP')
    return (expected_traces, expected_holes, expected_segments)

def PK_P07__validate_publisher_document(raw, chunk_rows, result_row, result_line, operation, source_sha, header, result, branches, net_order, groups, pads):
    document = PK_P07_strict_json(raw)
    PK_P07_keys(document, ('schema', 'context', 'objects', 'branch', 'grid', 'checkpoints', 'path', 'publisher', 'ordering', 'before', 'after', 'integrity', 'limitations'), 'PUB_TARGET_SCHEMA')
    PK_P07_need(document['schema'] == PK_P07_PUBLISHER_TARGET_SCHEMA, 'PUB_TARGET_SCHEMA')
    ctx = document['context']
    PK_P07_keys(ctx, ('sourceManifestSha256', 'operation', 'rootSeed', 'placementSeed', 'routingSeed', 'attempt', 'request', 'pass', 'placementSha256'), 'PUB_CONTEXT_SCHEMA')
    candidate = header['candidate']
    receipt_context = result['context']
    PK_P07_need(ctx['sourceManifestSha256'] == source_sha and PK_P07_integer(ctx['operation'], 4, 4) and (ctx['operation'] == operation) and PK_P07_integer(ctx['request'], 0, 0) and PK_P07_integer(ctx['pass'], 1, 1) and all((type(ctx[name]) is str and ctx[name] == candidate[candidate_name] for name, candidate_name in (('rootSeed', 'rootSeed'), ('placementSeed', 'placementSeed'), ('routingSeed', 'routingSeed')))) and PK_P07_integer(ctx['attempt'], 0, 5) and (ctx['attempt'] == receipt_context['selectedAttempt']) and (ctx['request'] == receipt_context['requestIndex']) and PK_P07_digest(ctx['placementSha256']) and (ctx['placementSha256'] == receipt_context['pristineSha256']), 'PUB_CONTEXT_JOIN')
    if header['mode'] == 'AUTHORED':
        PK_P07_need((ctx['rootSeed'], ctx['placementSeed'], ctx['routingSeed'], ctx['attempt']) == ('9007199254740993', '-6587958367974952701', '8445301674744508279', 4), 'PUB_AUTHORED_CONTEXT')
    else:
        PK_P07_need(header['mode'] == 'TINY_CANARY' and (ctx['rootSeed'], ctx['placementSeed'], ctx['routingSeed']) == ('42', '5526715536693200536', '4493419427463069983'), 'PUB_TINY_CONTEXT')
    objects = document['objects']
    object_names = ('generator', 'route', 'search', 'branch', 'layout', 'publisher', 'found', 'terminal')
    PK_P07_keys(objects, object_names, 'PUB_OBJECTS_SCHEMA')
    for name in object_names:
        if name == 'terminal':
            PK_P07_need(objects[name] is None or PK_P07_integer(objects[name], 1), 'PUB_OBJECT_TOKEN')
        else:
            PK_P07_need(PK_P07_integer(objects[name], 1), 'PUB_OBJECT_TOKEN')
    nonnull_objects = [objects[name] for name in object_names if objects[name] is not None]
    PK_P07_need(len(nonnull_objects) == len(set(nonnull_objects)), 'PUB_OBJECT_ALIAS')
    branch = document['branch']
    PK_P07_keys(branch, ('ordinal', 'netVectorPosition', 'netId', 'startPadId', 'rootPadId', 'reachedPadIds', 'foundTerminalPadId', 'branchRecordGroup'), 'PUB_BRANCH_SCHEMA')
    PK_P07_need(PK_P07_integer(branch['ordinal']) and PK_P07_integer(branch['netVectorPosition']) and PK_P07_integer(branch['branchRecordGroup']), 'PUB_BRANCH_IDENTITY')
    candidates = [row for row in branches if row['ordinal'] == branch['ordinal'] and row['snapshot']['pass'] == ctx['pass'] and (row['snapshot']['terminalReason'] == 'FOUND')]
    PK_P07_need(len(candidates) == 1, 'PUB_BRANCH_UNIQUE')
    branch_row = candidates[0]
    snapshot = branch_row['snapshot']
    PK_P07_need(branch['branchRecordGroup'] == branch_row['group'] and branch['netId'] == snapshot['netId'] and (branch['startPadId'] == snapshot['startPadId']) and (branch['rootPadId'] == snapshot['rootPadId']) and (branch['foundTerminalPadId'] is None or PK_P07_token(branch['foundTerminalPadId'])), 'PUB_BRANCH_JOIN')
    PK_P07_need(type(branch['reachedPadIds']) is list and all((PK_P07_token(pad_id) for pad_id in branch['reachedPadIds'])) and (branch['reachedPadIds'] == sorted(set(branch['reachedPadIds']))) and (len(branch['reachedPadIds']) == snapshot['reachedPadCount']) and (branch['rootPadId'] in branch['reachedPadIds']) and (branch['startPadId'] not in branch['reachedPadIds']), 'PUB_REACHED_SET')
    reached_from_callbacks = {branch['rootPadId']}
    reached_from_callbacks.update((row['snapshot']['startPadId'] for row in branches if row['snapshot']['pass'] == ctx['pass'] and row['snapshot']['netId'] == branch['netId'] and (row['ordinal'] < branch['ordinal'])))
    PK_P07_need(branch['reachedPadIds'] == sorted(reached_from_callbacks), 'PUB_REACHED_CALLBACK_JOIN')
    PK_P07_need(net_order is not None and branch['netVectorPosition'] < len(net_order['netIds']) and (net_order['netIds'][branch['netVectorPosition']] == branch['netId']), 'PUB_NET_VECTOR_JOIN')
    if header['mode'] == 'AUTHORED':
        PK_P07_need((branch['netId'], branch['startPadId'], branch['rootPadId'], snapshot['terminalReason']) == ('OUT_F', 'RSH_F.1', 'CSTORE_F.2', 'FOUND'), 'PUB_AUTHORED_BRANCH')
    pads_by_id = {row['padId']: row for row in pads}
    net_roles = {}
    for pad in pads:
        previous = net_roles.setdefault(pad['netId'], pad['role'])
        PK_P07_need(previous == pad['role'], 'PUB_NET_ROLE')
    PK_P07_need(branch['netId'] in net_roles and branch['startPadId'] in pads_by_id and (branch['rootPadId'] in pads_by_id) and (pads_by_id[branch['startPadId']]['netId'] == branch['netId']) and (pads_by_id[branch['rootPadId']]['netId'] == branch['netId']), 'PUB_PAD_JOIN')
    for pad_id in branch['reachedPadIds']:
        PK_P07_need(pad_id in pads_by_id and pads_by_id[pad_id]['netId'] == branch['netId'], 'PUB_REACHED_PAD')
    terminal_id = branch['foundTerminalPadId']
    PK_P07_need(terminal_id is None or (terminal_id in pads_by_id and pads_by_id[terminal_id]['netId'] == branch['netId'] and (terminal_id in branch['reachedPadIds'])), 'PUB_TERMINAL_PAD')
    grid = document['grid']
    PK_P07_keys(grid, ('width', 'height', 'cells', 'originX', 'originY', 'pitch', 'primaryLayer', 'maximumVias'), 'PUB_GRID_SCHEMA')
    PK_P07_need(PK_P07_integer(grid['width'], 1, PK_P07_LIMITS['cells']) and PK_P07_integer(grid['height'], 1, PK_P07_LIMITS['cells']) and PK_P07_integer(grid['cells'], 1, PK_P07_LIMITS['cells']) and (grid['cells'] == grid['width'] * grid['height']) and (grid['cells'] <= PK_P07_LIMITS['cells']) and ((grid['width'], grid['height']) == (snapshot['gridWidth'], snapshot['gridHeight'])) and PK_P07__point_coordinate(grid['originX']) and PK_P07__point_coordinate(grid['originY']) and PK_P07_integer(grid['pitch'], 10, 10) and PK_P07_integer(grid['primaryLayer'], 0, 1) and PK_P07_integer(grid['maximumVias'], 0, PK_P07_LIMITS['holeCap']), 'PUB_GRID_VALUES')
    for pad_id in (branch['startPadId'], branch['rootPadId']):
        pad = pads_by_id[pad_id]
        dx, dy = (pad['x'] - grid['originX'], pad['y'] - grid['originY'])
        PK_P07_need(dx >= 0 and dy >= 0 and (dx % grid['pitch'] == 0) and (dy % grid['pitch'] == 0) and (dx // grid['pitch'] < grid['width']) and (dy // grid['pitch'] < grid['height']), 'PUB_PAD_GRID')
    path = document['path']
    PK_P07_keys(path, ('encoding', 'nodes', 'nodeTokens', 'foundGoal'), 'PUB_PATH_SCHEMA')
    PK_P07_need(path['encoding'] == 'cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow' and type(path['nodes']) is list and (1 <= len(path['nodes']) <= PK_P07_LIMITS['pathNodes']) and (type(path['nodeTokens']) is list) and (len(path['nodeTokens']) == len(path['nodes'])) and all((PK_P07_integer(value, 1) for value in path['nodeTokens'])) and (len(set(path['nodeTokens'])) == len(path['nodeTokens'])) and (path['nodeTokens'][-1] == objects['found']) and (not set(path['nodeTokens']) - {objects['found']} & set(nonnull_objects)) and (type(path['foundGoal']) is bool) and (path['foundGoal'] is True), 'PUB_PATH_HEADER')
    nodes = path['nodes']
    for index, node in enumerate(nodes):
        PK_P07_need(type(node) is list and len(node) == 11 and all((PK_P07_integer(value, -2 ** 31, 2 ** 31 - 1) for value in node)), 'PUB_PATH_NODE')
        cell, layer, x, y, direction, transitions, cost, heuristic, serial, key_value, parent = node
        PK_P07_need(PK_P07_integer(cell, 0, grid['cells'] - 1) and PK_P07_integer(layer, 0, 1) and PK_P07__point_coordinate(x) and PK_P07__point_coordinate(y) and PK_P07_integer(direction, 0, 4) and PK_P07_integer(transitions, 0, PK_P07_LIMITS['transitionCap']) and PK_P07_integer(cost, 0, 2 ** 31 - 1) and PK_P07_integer(heuristic, 0, 2 ** 31 - 1) and PK_P07_integer(serial, 0, 2 ** 31 - 1) and PK_P07_integer(key_value, 0, 2 ** 31 - 1) and (parent == (-1 if index == 0 else index - 1)), 'PUB_PATH_NODE_DOMAIN')
        PK_P07_need((x, y) == (grid['originX'] + cell % grid['width'] * grid['pitch'], grid['originY'] + cell // grid['width'] * grid['pitch']), 'PUB_PATH_GRID')
        PK_P07_need(key_value == ((transitions * 2 + layer) * 5 + direction) * grid['cells'] + cell, 'PUB_PATH_KEY')
        if index == 0:
            PK_P07_need(direction == 4 and transitions == 0, 'PUB_PATH_ROOT')
        else:
            previous = nodes[index - 1]
            same_layer = layer == previous[1]
            dx, dy = (x - previous[2], y - previous[3])
            if same_layer:
                PK_P07_need(abs(dx) + abs(dy) == grid['pitch'] and direction in range(4) and (transitions == previous[5]) and (cost >= previous[6]) and (serial > previous[8]), 'PUB_PATH_STEP')
                expected_direction = {(0, -grid['pitch']): 0, (grid['pitch'], 0): 1, (0, grid['pitch']): 2, (-grid['pitch'], 0): 3}.get((dx, dy))
                PK_P07_need(direction == expected_direction, 'PUB_PATH_DIRECTION')
            else:
                PK_P07_need((dx, dy) == (0, 0) and direction == 4 and (transitions == previous[5] + 1) and (cost >= previous[6]) and (serial > previous[8]), 'PUB_PATH_VIA_STEP')
    first_node, found_node = (nodes[0], nodes[-1])
    start_pad = pads_by_id[branch['startPadId']]
    PK_P07_need((first_node[2], first_node[3]) == (start_pad['x'], start_pad['y']) and (first_node[2], first_node[3]) == (snapshot['startX'], snapshot['startY']) and (first_node[1] == grid['primaryLayer']), 'PUB_PATH_START')
    endpoint_ids = set(branch['reachedPadIds']) | {branch['rootPadId']}
    if terminal_id is not None:
        PK_P07_need((pads_by_id[terminal_id]['x'], pads_by_id[terminal_id]['y']) == (found_node[2], found_node[3]), 'PUB_PATH_TERMINAL_ENDPOINT')
    else:
        PK_P07_need(PK_P07__prefix_goal_supported(document, pads_by_id, endpoint_ids, found_node[2], found_node[3], found_node[1], branch['netId']), 'PUB_PATH_GOAL_ENDPOINT')
    PK_P07_need((objects['terminal'] is None) == (terminal_id is None), 'PUB_TERMINAL_OBJECT_JOIN')
    checkpoints = document['checkpoints']
    checkpoint_names = ('found', 'publisherSeen', 'publisherTerminal', 'orderingReady', 'orderingResult')
    PK_P07_keys(checkpoints, checkpoint_names, 'PUB_CHECKPOINT_SCHEMA')
    checkpoint_values = []
    observers_by_group = {}
    cumulative_observers = []
    total_observers = 0
    for group in groups:
        total_observers += group['observerChecks']
        cumulative_observers.append(total_observers)
        observers_by_group[group['ordinal']] = group['observerChecks']
    for name in checkpoint_names:
        value = checkpoints[name]
        if value is None:
            checkpoint_values.append(None)
            continue
        PK_P07_keys(value, ('group', 'sequence'), 'PUB_CHECKPOINT_VALUE')
        PK_P07_need(PK_P07_integer(value['group'], 0, len(groups) - 1) and PK_P07_integer(value['sequence'], 1, 2 ** 31 - 1), 'PUB_CHECKPOINT_VALUE')
        group = groups[value['group']]
        PK_P07_need(group['phase'] == 'ADVANCE' and group['p07Slices'] == 1 and (group['observerChecks'] >= 1) and (value['sequence'] <= cumulative_observers[value['group']]), 'PUB_CHECKPOINT_GROUP')
        checkpoint_values.append(value)
    populated = [value for value in checkpoint_values if value is not None]
    PK_P07_need(len(populated) >= 1 and all((a['sequence'] <= b['sequence'] and a['group'] <= b['group'] for a, b in zip(populated, populated[1:]))), 'PUB_CHECKPOINT_ORDER')
    for left, right in zip(populated, populated[1:]):
        if left['sequence'] == right['sequence']:
            PK_P07_need(left['group'] == right['group'], 'PUB_CHECKPOINT_COOBSERVATION')
        else:
            PK_P07_need(left['sequence'] < right['sequence'], 'PUB_CHECKPOINT_POLL_SEQUENCE')
    by_group = {}
    for value in populated:
        by_group.setdefault(value['group'], set()).add(value['sequence'])
    PK_P07_need(all((len(sequences) <= observers_by_group[group] for group, sequences in by_group.items())), 'PUB_CHECKPOINT_OBSERVER_COUNT')
    found_checkpoint = checkpoints['found']
    PK_P07_need(found_checkpoint is not None and found_checkpoint['group'] == branch_row['group'], 'PUB_FOUND_CHECKPOINT')
    PK_P07_need(checkpoints['publisherSeen'] is not None and checkpoints['publisherTerminal'] is not None and (checkpoints['orderingResult'] is not None), 'PUB_REQUIRED_CHECKPOINT')
    before_fingerprint, before_raw = PK_P07__geometry_rows(document['before'])
    after_fingerprint, after_raw = PK_P07__geometry_rows(document['after'])
    PK_P07_need(len(document['before']['holes']) == snapshot['prefixViaCount'], 'PUB_PREFIX_VIA_COUNT')
    PK_P07_need(document['before']['staticPrefix'] == document['after']['staticPrefix'], 'PUB_STATIC_PREFIX_CHANGED')
    PK_P07_need(document['before']['geometrySha256'] == branch_row['prefixSha256'] and document['before']['geometryBytes'] == branch_row['prefixBytes'], 'PUB_BRANCH_PREFIX_JOIN')
    PK_P07_need(branch_row['prefixSha256'] == PK_P07_sha(before_raw) and branch_row['prefixBytes'] == len(before_raw), 'PUB_BRANCH_PREFIX_RAW')
    before_traces = PK_P07_Counter((PK_P07__freeze(row) for row in document['before']['traces']))
    after_traces = PK_P07_Counter((PK_P07__freeze(row) for row in document['after']['traces']))
    before_holes = PK_P07_Counter((PK_P07__freeze(row) for row in document['before']['holes']))
    after_holes = PK_P07_Counter((PK_P07__freeze(row) for row in document['after']['holes']))
    PK_P07_need(before_traces <= after_traces and before_holes <= after_holes, 'PUB_PREFIX_ROWS_NOT_CONSERVED')
    publisher = document['publisher']
    PK_P07_keys(publisher, ('outcome', 'stage', 'qualityIndex', 'reversedSize', 'storedLength', 'storedBends', 'storedLastDirection', 'segments', 'reason', 'sourceHandoff', 'startReachedAfter'), 'PUB_PUBLISHER_SCHEMA')
    PK_P07_need(publisher['outcome'] in ('PUBLISHED', 'REJECTED'), 'PUB_CAPTURE_INCOMPLETE')
    PK_P07_need(PK_P07_integer(publisher['stage'], 0, 6) and PK_P07_integer(publisher['qualityIndex'], 0, PK_P07_LIMITS['pathNodes']) and PK_P07_integer(publisher['reversedSize'], 1, PK_P07_LIMITS['pathNodes']) and PK_P07_integer(publisher['storedLength'], 0, PK_P07_LIMITS['pathNodes'] * 10) and PK_P07_integer(publisher['storedBends'], 0, PK_P07_LIMITS['pathNodes'] - 1) and PK_P07_integer(publisher['storedLastDirection'], 0, 4) and PK_P07_integer(publisher['segments'], 0, PK_P07_LIMITS['pathNodes'] - 1) and (type(publisher['sourceHandoff']) is bool) and (type(publisher['startReachedAfter']) is bool) and (publisher['reason'] is None or (type(publisher['reason']) is str and 0 < len(publisher['reason'].encode('utf-8')) <= 256)), 'PUB_PUBLISHER_VALUES')
    PK_P07_need(publisher['reversedSize'] == len(nodes) and publisher['qualityIndex'] == len(nodes), 'PUB_PUBLISHER_PATH_SIZE')
    path_length = 0
    bends = 0
    last_direction = 4
    transitions = 0
    for previous, current in zip(nodes, nodes[1:]):
        if previous[1] != current[1]:
            transitions += 1
            last_direction = 4
        else:
            path_length += grid['pitch']
            if last_direction != 4 and last_direction != current[4]:
                bends += 1
            last_direction = current[4]
    PK_P07_need(transitions == found_node[5] and transitions <= PK_P07_LIMITS['transitionCap'], 'PUB_PATH_TRANSITIONS')
    direct = abs(start_pad['x'] - found_node[2]) + abs(start_pad['y'] - found_node[3])
    predicates = (direct == 0, path_length > direct * 3, bends > 16)
    PK_P07_need((publisher['storedLength'], publisher['storedBends'], publisher['storedLastDirection']) == (path_length, bends, last_direction), 'PUB_STORED_GUARD')
    expected_traces, expected_holes, expected_segments = PK_P07__publisher_geometry_delta(document, pads_by_id, net_roles, branches)
    ordering = document['ordering']
    PK_P07_keys(ordering, ('routerStatus', 'routerReason', 'readyLayoutToken', 'readyPass', 'finalizerStatus', 'finalizedBefore', 'finalizedAfter', 'lastFailureDetail'), 'PUB_ORDERING_SCHEMA')
    PK_P07_need(ordering['routerStatus'] in ('READY', 'ROUTER_REJECTED') and type(ordering['routerReason']) is str and (0 < len(ordering['routerReason'].encode('utf-8')) <= 256) and (ordering['finalizerStatus'] in ('NOT_REACHED', 'FINALIZED', 'REJECTED')), 'PUB_ORDERING_VALUES')
    PK_P07_need(ordering['lastFailureDetail'] is None or (type(ordering['lastFailureDetail']) is str and len(ordering['lastFailureDetail'].encode('utf-8')) <= 4096), 'PUB_FINALIZER_DETAIL')
    if ordering['routerStatus'] == 'READY':
        PK_P07_need(PK_P07_integer(ordering['readyLayoutToken'], 1) and ordering['readyLayoutToken'] == objects['layout'] and PK_P07_integer(ordering['readyPass'], 1, 1) and (checkpoints['orderingReady'] is not None) and (checkpoints['orderingResult'] is not None) and (checkpoints['orderingReady']['sequence'] < checkpoints['orderingResult']['sequence']), 'PUB_READY_JOIN')
        PK_P07_need(ordering['finalizerStatus'] in ('FINALIZED', 'REJECTED') and PK_P07_integer(ordering['finalizedBefore'], 0, 3) and PK_P07_integer(ordering['finalizedAfter'], 0, 3), 'PUB_FINALIZER_JOIN')
        if ordering['finalizerStatus'] == 'FINALIZED':
            PK_P07_need(ordering['finalizedAfter'] == ordering['finalizedBefore'] + 1, 'PUB_FINALIZED_COUNTER')
        else:
            PK_P07_need(ordering['finalizedAfter'] == ordering['finalizedBefore'] and type(ordering['lastFailureDetail']) is str and (len(ordering['lastFailureDetail'].encode('utf-8')) > 0), 'PUB_REJECTED_COUNTER')
    else:
        PK_P07_need(ordering['readyLayoutToken'] is None and ordering['readyPass'] is None and (checkpoints['orderingReady'] is None) and (ordering['finalizerStatus'] == 'NOT_REACHED') and PK_P07_integer(ordering['finalizedBefore'], 0, 3) and PK_P07_integer(ordering['finalizedAfter'], 0, 3) and (ordering['finalizedBefore'] == ordering['finalizedAfter']), 'PUB_ROUTER_REJECTED_JOIN')
    PK_P07_need(ordering['finalizedAfter'] <= result['receipt']['finalizedOrderings'], 'PUB_REQUEST_RECEIPT_JOIN')
    if ordering['finalizerStatus'] == 'FINALIZED':
        PK_P07_need(result['receipt']['selectedOrdering'] >= 0 and result['route'] == 'REQUEST_FINALIZED' and (result['winner'] is not None), 'PUB_FINALIZED_REQUEST')
    if publisher['outcome'] == 'PUBLISHED':
        PK_P07_need(not any(predicates) and publisher['stage'] == 6 and (publisher['reason'] is None) and (publisher['sourceHandoff'] is True) and (publisher['startReachedAfter'] is True) and (publisher['segments'] == expected_segments), 'PUB_PUBLISHED_BOUNDARY')
        PK_P07_need(after_traces == before_traces + PK_P07_Counter((PK_P07__freeze(row) for row in expected_traces)) and after_holes == before_holes + PK_P07_Counter((PK_P07__freeze(row) for row in expected_holes)), 'PUB_GEOMETRY_DELTA')
    else:
        expected_reason = branch['netId'] + ':BRANCH_QUALITY_LIMIT'
        PK_P07_need(any(predicates) and publisher['stage'] == 1 and (publisher['reason'] == expected_reason) and (publisher['sourceHandoff'] is False) and (publisher['startReachedAfter'] is False) and (publisher['segments'] == 0) and (ordering['routerStatus'] == 'ROUTER_REJECTED') and (ordering['routerReason'] == expected_reason) and (before_fingerprint == after_fingerprint) and (document['before'] == document['after']), 'PUB_REJECTED_BOUNDARY')
    integrity = document['integrity']
    integrity_names = ('ownerBindingVerified', 'immutablePathVerified', 'staticGeometryEqual', 'prefixRowsConserved', 'sourceReferencesReleased')
    PK_P07_keys(integrity, integrity_names, 'PUB_INTEGRITY_SCHEMA')
    PK_P07_need(all((type(integrity[name]) is bool and integrity[name] is True for name in integrity_names)), 'PUB_INTEGRITY')
    limitations = document['limitations']
    PK_P07_keys(limitations, ('routeAdoption', 'layoutQualification', 'playable100', 'fullPhysicalProof'), 'PUB_LIMITATIONS_SCHEMA')
    PK_P07_need(type(limitations['routeAdoption']) is bool and limitations['routeAdoption'] is False and (type(limitations['layoutQualification']) is bool) and (limitations['layoutQualification'] is False) and (type(limitations['playable100']) is int) and (limitations['playable100'] == 0) and (type(limitations['fullPhysicalProof']) is str) and (limitations['fullPhysicalProof'] == 'NOT_RUN'), 'PUB_LIMITATIONS')
    PK_P07_need(result_row['targetId'] == PK_P07_PUBLISHER_TARGET_ID and result_row['payloadBytes'] == len(raw) and (result_row['chunkCount'] == len(chunk_rows)) and PK_P07_digest(result_row['payloadSha256']) and (result_row['payloadSha256'] == PK_P07_sha(raw)), 'PUB_RESULT_SEAL')
    return dict(document=document, payloadBytes=len(raw), payloadSha256=PK_P07_sha(raw), captureHashBytes=len(raw) + document['before']['geometryBytes'] + document['after']['geometryBytes'] + sum((len(line) for _row, line in chunk_rows)) + len(result_line), geometryBytes=document['before']['geometryBytes'] + document['after']['geometryBytes'])

def PK_P07__json_space(raw, position):
    while position < len(raw) and raw[position] in b' \t\r\n':
        position += 1
    return position

def PK_P07__json_string_end(raw, position):
    PK_P07_need(position < len(raw) and raw[position] == 34, 'GRAPH_BODY_EXTRACT')
    position += 1
    while position < len(raw):
        value = raw[position]
        if value == 34:
            return position + 1
        if value == 92:
            position += 2
        else:
            position += 1
    raise ValueError('GRAPH_BODY_EXTRACT')

def PK_P07__json_value_end(raw, position):
    position = PK_P07__json_space(raw, position)
    PK_P07_need(position < len(raw), 'GRAPH_BODY_EXTRACT')
    if raw[position] == 34:
        return PK_P07__json_string_end(raw, position)
    if raw[position] not in (91, 123):
        end = position
        while end < len(raw) and raw[end] not in b',]} \t\r\n':
            end += 1
        PK_P07_need(end > position, 'GRAPH_BODY_EXTRACT')
        return end
    stack = [125 if raw[position] == 123 else 93]
    position += 1
    while position < len(raw) and stack:
        value = raw[position]
        if value == 34:
            position = PK_P07__json_string_end(raw, position)
            continue
        if value == 123:
            stack.append(125)
        elif value == 91:
            stack.append(93)
        elif value in (125, 93):
            PK_P07_need(value == stack[-1], 'GRAPH_BODY_EXTRACT')
            stack.pop()
            if not stack:
                return position + 1
        position += 1
    raise ValueError('GRAPH_BODY_EXTRACT')

def PK_P07__top_level_value_bytes(raw, wanted):
    """Return one top-level JSON value's exact token bytes, excluding whitespace."""
    position = PK_P07__json_space(raw, 0)
    PK_P07_need(position < len(raw) and raw[position] == 123, 'GRAPH_BODY_EXTRACT')
    position += 1
    while True:
        position = PK_P07__json_space(raw, position)
        PK_P07_need(position < len(raw), 'GRAPH_BODY_EXTRACT')
        if raw[position] == 125:
            break
        key_end = PK_P07__json_string_end(raw, position)
        key = PK_P07_strict_json(raw[position:key_end])
        PK_P07_need(type(key) is str, 'GRAPH_BODY_EXTRACT')
        position = PK_P07__json_space(raw, key_end)
        PK_P07_need(position < len(raw) and raw[position] == 58, 'GRAPH_BODY_EXTRACT')
        value_start = PK_P07__json_space(raw, position + 1)
        value_end = PK_P07__json_value_end(raw, value_start)
        if key == wanted:
            return raw[value_start:value_end]
        position = PK_P07__json_space(raw, value_end)
        PK_P07_need(position < len(raw), 'GRAPH_BODY_EXTRACT')
        if raw[position] == 44:
            position += 1
            continue
        PK_P07_need(raw[position] == 125, 'GRAPH_BODY_EXTRACT')
        break
    raise ValueError('GRAPH_BODY_MISSING')

def PK_P07__canonical_graph_body(body):
    stability_names = ('prefixGeometrySha256', 'liveBeforeVerified', 'liveAfterVerified', 'liveEffectsUnchanged', 'goalVerified', 'originalPathVerified')
    grid_names = ('width', 'height', 'cells', 'originX', 'originY', 'pitch', 'startCell', 'initialLayerMask', 'endCell', 'endLayer', 'goalUnionCellCount')
    path_names = ('nodeCount', 'planarEdgeCount', 'transitionCount', 'tupleSha256')
    work_names = ('advanceCalls', 'workSteps', 'copyEntries', 'comparisonEntries', 'geometryBytes', 'effectUnits', 'ordinaryQueries', 'goalCells', 'reachedPadChecks', 'encodedCells', 'pathNodes', 'pathEdgesVerified', 'tupleBytes', 'serializedUtf16Units', 'activeMillis')
    masks_names = ('topStepsHex', 'bottomStepsHex', 'goalFacesHex')

    def quoted(value):
        PK_P07_need(type(value) is str and value.isascii(), 'GRAPH_BODY_ASCII')
        return PK_P07_json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode('ascii')

    def scalar(value):
        if type(value) is str:
            return quoted(value)
        if type(value) is bool:
            return b'true' if value else b'false'
        PK_P07_need(type(value) is int, 'GRAPH_BODY_SCALAR')
        return str(value).encode('ascii')

    def obj(fields):
        return b'{' + b','.join((quoted(name) + b':' + value for name, value in fields)) + b'}'
    stability = obj(((name, scalar(body['stability'][name])) for name in stability_names))
    grid = obj(((name, scalar(body['grid'][name])) for name in grid_names))
    path = obj(((name, scalar(body['path'][name])) for name in path_names))
    work = obj(((name, str(body['work'][name]).rjust(19, ' ').encode('ascii')) for name in work_names))
    masks = obj(((name, scalar(body['masks'][name])) for name in masks_names))
    return obj((('schema', scalar(body['schema'])), ('stability', stability), ('grid', grid), ('path', path), ('work', work), ('masks', masks)))

def PK_P07__graph_distances(document):
    """Compute directed ordinary-edge distances independently by BFS and Dijkstra."""
    body = document['body']
    grid = body['grid']
    masks = body['masks']
    width, height, cells = (grid['width'], grid['height'], grid['cells'])
    start = grid['startCell']
    top, bottom = (masks['topStepsHex'], masks['bottomStepsHex'])
    bfs_results = []
    for layer, mask in enumerate((top, bottom)):
        distance = [None] * cells
        parent = [None] * cells
        distance[start] = 0
        queue = PK_P07_deque([start])
        while queue:
            cell = queue.popleft()
            x, y = (cell % width, cell // width)
            flags = int(mask[cell], 16)
            neighbors = ((1, cell - width, y > 0), (2, cell + 1, x + 1 < width), (4, cell + width, y + 1 < height), (8, cell - 1, x > 0))
            for bit, neighbor, inside in neighbors:
                if inside and flags & bit and (distance[neighbor] is None):
                    distance[neighbor] = distance[cell] + 1
                    parent[neighbor] = cell
                    queue.append(neighbor)
        bfs_results.append((distance, parent))
    dijkstra_results = []
    for layer, mask in enumerate((top, bottom)):
        distance = [None] * cells
        distance[start] = 0
        pending = [(0, start)]
        while pending:
            physical, cell = PK_P07_heapq.heappop(pending)
            if distance[cell] != physical:
                continue
            x, y = (cell % width, cell // width)
            flags = int(mask[cell], 16)
            candidates = ((8, x - 1, y, x > 0), (4, x, y + 1, y + 1 < height), (2, x + 1, y, x + 1 < width), (1, x, y - 1, y > 0))
            for bit, nx, ny, inside in candidates:
                if not inside or not flags & bit:
                    continue
                neighbor = ny * width + nx
                candidate = physical + grid['pitch']
                if distance[neighbor] is None or candidate < distance[neighbor]:
                    distance[neighbor] = candidate
                    PK_P07_heapq.heappush(pending, (candidate, neighbor))
        dijkstra_results.append(distance)
    return [dict(layer=layer, bfsEdges=bfs_results[layer][0], dijkstraLength=dijkstra_results[layer], bfsParent=bfs_results[layer][1]) for layer in range(2)]

def PK_P07_analyze_graph(document):
    """Return bounded per-seed-face ordinary-edge length diagnostics."""
    body = document['body']
    grid = body['grid']
    masks = body['masks']
    width, height, cells, pitch = (grid['width'], grid['height'], grid['cells'], grid['pitch'])
    start = grid['startCell']
    initial_mask = document['source']['initialLayerMask']
    sx = grid['originX'] + start % width * pitch
    sy = grid['originY'] + start // width * pitch
    found_cell, found_layer = (grid['endCell'], grid['endLayer'])
    distances = PK_P07__graph_distances(document)
    faces = []

    def make_witness(layer, cell, owner):
        bfs, dijkstra, parent = (owner['bfsEdges'], owner['dijkstraLength'], owner['bfsParent'])
        reverse = [cell]
        current = cell
        while current != start:
            current = parent[current]
            PK_P07_need(current is not None and len(reverse) <= cells, 'GRAPH_BFS_PARENT')
            reverse.append(current)
        route = list(reversed(reverse))
        mask = masks['topStepsHex' if layer == 0 else 'bottomStepsHex']
        for left, right in zip(route, route[1:]):
            lx, ly = (left % width, left // width)
            rx, ry = (right % width, right // width)
            bit = 1 if rx == lx and ry == ly - 1 else 2 if rx == lx + 1 and ry == ly else 4 if rx == lx and ry == ly + 1 else 8 if rx == lx - 1 and ry == ly else 0
            PK_P07_need(bit != 0 and int(mask[left], 16) & bit, 'GRAPH_WITNESS_EDGE')
        edges = bfs[cell]
        length = dijkstra[cell]
        PK_P07_need(edges is not None and length == edges * pitch, 'GRAPH_WITNESS_DISTANCE')
        direct = abs(cell % width * pitch + grid['originX'] - sx) + abs(cell // width * pitch + grid['originY'] - sy)
        preimage = '\n'.join((f'{index},{layer},{node}' for index, node in enumerate(route))).encode('ascii')
        return dict(cell=cell, x=grid['originX'] + cell % width * pitch, y=grid['originY'] + cell // width * pitch, direct=direct, edges=edges, length=length, pathNodeCount=len(route), pathSha256=PK_P07_sha(preimage))
    for owner in distances:
        layer = owner['layer']
        if not initial_mask & 1 << layer:
            continue
        goal_characters = [ord(ch) - 48 for ch in masks['goalFacesHex']]
        goals = [cell for cell, value in enumerate(goal_characters) if value & 1 << layer]
        bfs, dijkstra = (owner['bfsEdges'], owner['dijkstraLength'])
        for cell in goals:
            expected = None if bfs[cell] is None else bfs[cell] * pitch
            PK_P07_need(dijkstra[cell] == expected, 'GRAPH_ORACLE_DISAGREEMENT')
        reachable = [cell for cell in goals if dijkstra[cell] is not None]
        eligible = [cell for cell in reachable if abs(grid['originX'] + cell % width * pitch - sx) + abs(grid['originY'] + cell // width * pitch - sy) > 0 and dijkstra[cell] <= 3 * (abs(grid['originX'] + cell % width * pitch - sx) + abs(grid['originY'] + cell // width * pitch - sy))]
        shortest = min(reachable, key=lambda cell: (dijkstra[cell], cell), default=None)
        shortest_eligible = min(eligible, key=lambda cell: (dijkstra[cell], cell), default=None)
        faces.append(dict(layer=layer, goalFaceCount=len(goals), reachableGoalCount=len(reachable), lengthEligibleGoalCount=len(eligible), shortestReachable=None if shortest is None else make_witness(layer, shortest, owner), shortestLengthEligible=None if shortest_eligible is None else make_witness(layer, shortest_eligible, owner), foundEndpointLength=dijkstra[found_cell] if found_layer == layer else None))
    return faces

def PK_P07_validate_graph_document(raw, graph_chunks, result_row, result_line, publisher_capture, header, result, entry, source_sha, branches, net_order, groups, pads):
    document = PK_P07_strict_json(raw)
    PK_P07_keys(document, ('schema', 'targetId', 'publisherPayloadSha256', 'context', 'objects', 'branch', 'source', 'goalFacesSha256', 'phaseWork', 'body', 'integrity', 'limitations'), 'GRAPH_DOCUMENT_SCHEMA')
    PK_P07_need(document['schema'] == PK_P07_GRAPH_TARGET_SCHEMA and document['targetId'] == PK_P07_GRAPH_TARGET_ID, 'GRAPH_DOCUMENT_IDENTITY')
    PK_P07_need(type(graph_chunks) is list and graph_chunks, 'GRAPH_CHUNKS_MISSING')
    payload = b''
    for index, (row, line, decoded) in enumerate(graph_chunks):
        PK_P07_keys(row, PK_P07_GRAPH_CHUNK_FIELDS, 'GRAPH_CHUNK_SCHEMA')
        PK_P07_need(row['record'] == 'GRAPH_CHUNK' and PK_P07_integer(row['operation'], 4, 4) and (row['targetId'] == PK_P07_GRAPH_TARGET_ID) and PK_P07_integer(row['group'], 0, 639) and PK_P07_integer(row['index'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and (row['index'] == index) and PK_P07_integer(row['offset'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and (row['offset'] == len(payload)) and PK_P07_integer(row['bytes'], 1, PK_P07_LIMITS['publisherChunkBytes']) and (type(row['data']) is str), 'GRAPH_CHUNK_IDENTITY')
        try:
            encoded = row['data'].encode('ascii')
            actual = PK_P07_base64.b64decode(encoded, validate=True)
        except (UnicodeEncodeError, ValueError):
            raise ValueError('GRAPH_CHUNK_BASE64')
        PK_P07_need(PK_P07_base64.b64encode(actual).decode('ascii') == row['data'] and actual == decoded and (len(actual) == row['bytes']), 'GRAPH_CHUNK_BASE64')
        payload += actual
        PK_P07_need(len(payload) <= PK_P07_LIMITS['publisherPayloadBytes'], 'GRAPH_PAYLOAD_LIMIT')
    PK_P07_keys(result_row, PK_P07_GRAPH_RESULT_FIELDS, 'GRAPH_RESULT_SCHEMA')
    PK_P07_need(type(result_line) is bytes and result_row['record'] == 'GRAPH_RESULT' and PK_P07_integer(result_row['operation'], 4, 4) and (result_row['targetId'] == PK_P07_GRAPH_TARGET_ID) and PK_P07_integer(result_row['group'], 0, 639) and PK_P07_integer(result_row['payloadBytes'], 1, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_digest(result_row['payloadSha256']) and PK_P07_integer(result_row['chunkCount'], 1, PK_P07_LIMITS['publisherPayloadBytes']) and (result_row['payloadBytes'] == len(payload)) and (result_row['chunkCount'] == len(graph_chunks)) and (result_row['payloadSha256'] == PK_P07_sha(payload)) and (payload == raw), 'GRAPH_RESULT_SEAL')
    publisher = publisher_capture['document']
    PK_P07_need(document['publisherPayloadSha256'] == publisher_capture['payloadSha256'], 'GRAPH_PUBLISHER_SEAL_JOIN')
    context = document['context']
    PK_P07_keys(context, ('sourceManifestSha256', 'operation', 'rootSeed', 'placementSeed', 'routingSeed', 'attempt', 'request', 'pass', 'placementSha256'), 'GRAPH_CONTEXT_SCHEMA')
    PK_P07_need(PK_P07_same_json_value(context, publisher['context']) and context['sourceManifestSha256'] == source_sha, 'GRAPH_CONTEXT_JOIN')
    PK_P07_need(type(result) is dict and result['winner'] is None and (publisher['publisher']['outcome'] == 'REJECTED'), 'GRAPH_REQUEST_DISPOSITION')
    objects = document['objects']
    object_names = ('generator', 'route', 'search', 'branch', 'layout', 'publisher', 'found', 'terminal')
    PK_P07_keys(objects, object_names, 'GRAPH_OBJECTS_SCHEMA')
    PK_P07_need(PK_P07_same_json_value(objects, publisher['objects']), 'GRAPH_OBJECTS_JOIN')
    branch = document['branch']
    branch_names = ('ordinal', 'netVectorPosition', 'netId', 'startPadId', 'rootPadId', 'reachedPadIds', 'foundTerminalPadId', 'branchRecordGroup')
    PK_P07_keys(branch, branch_names, 'GRAPH_BRANCH_SCHEMA')
    PK_P07_need(PK_P07_same_json_value(branch, publisher['branch']), 'GRAPH_BRANCH_JOIN')
    branch_matches = [row for row in branches if row['ordinal'] == branch['ordinal'] and row['snapshot']['pass'] == context['pass'] and (row['snapshot']['terminalReason'] == 'FOUND')]
    PK_P07_need(len(branch_matches) == 1 and net_order is not None, 'GRAPH_BRANCH_SOURCE_JOIN')
    branch_row = branch_matches[0]
    pads_by_id = {row['padId']: row for row in pads}
    PK_P07_need(branch['startPadId'] in pads_by_id and branch['rootPadId'] in pads_by_id, 'GRAPH_ENDPOINT_PAD_JOIN')
    source = document['source']
    source_names = ('target', 'sourceKind', 'policy', 'initialLayerMask', 'activeTraceWidth', 'facesToken', 'routerTokens', 'goalArrayToken', 'reachedTerminalsArrayToken', 'startPadToken', 'rootPadToken', 'reachedPthPadIds')
    PK_P07_keys(source, source_names, 'GRAPH_SOURCE_SCHEMA')
    PK_P07_need(source['target'] == 'OUT_F' and source['sourceKind'] == 'OUT_F_REJECTED_PUBLISHER_PATH' and (source['policy'] == 'FULLER_TWO_LAYER') and PK_P07_integer(source['initialLayerMask'], 3, 3) and PK_P07_integer(source['activeTraceWidth'], 1, 2 ** 31 - 1), 'GRAPH_SOURCE_IDENTITY')
    roles = {row['netId']: row['role'] for row in pads}
    widths = {'HIGH_CURRENT': 13, 'SUPPLY': 11, 'RETURN': 11, 'CONTROL': 9, 'SIGNAL': 9}
    PK_P07_need(branch['netId'] in roles and roles[branch['netId']] in widths and (source['activeTraceWidth'] == widths[roles[branch['netId']]]), 'GRAPH_ACTIVE_ROLE_WIDTH')
    PK_P07_need(type(source['routerTokens']) is list and len(source['routerTokens']) == 2 and all((PK_P07_integer(value, 1, 2 ** 31 - 1) for value in source['routerTokens'])) and (type(source['reachedPthPadIds']) is list) and all((PK_P07_token(value) for value in source['reachedPthPadIds'])) and (source['reachedPthPadIds'] == sorted(set(source['reachedPthPadIds']))), 'GRAPH_SOURCE_ARRAYS')
    for name in ('facesToken', 'goalArrayToken', 'reachedTerminalsArrayToken', 'startPadToken', 'rootPadToken'):
        PK_P07_need(PK_P07_integer(source[name], 1, 2 ** 31 - 1), 'GRAPH_SOURCE_TOKEN')
    eligible_pth = set(branch['reachedPadIds']) - {branch['rootPadId'], branch['startPadId']}
    PK_P07_need(set(source['reachedPthPadIds']) <= eligible_pth and all((value in pads_by_id and pads_by_id[value]['netId'] == branch['netId'] for value in source['reachedPthPadIds'])), 'GRAPH_PTH_SUBSET')
    if header['mode'] == 'AUTHORED':
        PK_P07_need(source['reachedPthPadIds'] == [], 'GRAPH_AUTHORED_PTH_CENSUS')
    pub_tokens = {value for value in objects.values() if value is not None}
    node_tokens = set(publisher['path']['nodeTokens'])
    endpoint_tokens = (source['startPadToken'], source['rootPadToken'])
    container_tokens = (source['facesToken'], source['routerTokens'][0], source['routerTokens'][1], source['goalArrayToken'], source['reachedTerminalsArrayToken'])
    PK_P07_need(len(set(container_tokens)) == 5 and (not set(container_tokens) & (pub_tokens | node_tokens | set(endpoint_tokens))), 'GRAPH_CONTAINER_TOKEN_COLLISION')
    PK_P07_need(source['startPadToken'] != source['rootPadToken'], 'GRAPH_ENDPOINT_TOKEN_ALIAS')
    for pad_token, pad_id in zip(endpoint_tokens, (branch['startPadId'], branch['rootPadId'])):
        if branch['foundTerminalPadId'] == pad_id:
            PK_P07_need(objects['terminal'] is not None and pad_token == objects['terminal'], 'GRAPH_TERMINAL_TOKEN_JOIN')
        else:
            PK_P07_need(pad_token not in pub_tokens, 'GRAPH_PAD_TOKEN_COLLISION')
        PK_P07_need(pad_token not in node_tokens, 'GRAPH_NODE_TOKEN_COLLISION')
    phase_work = document['phaseWork']
    PK_P07_need(type(phase_work) is list and len(phase_work) == 32 and all((PK_P07_integer(value) for value in phase_work)), 'GRAPH_PHASE_WORK_SCHEMA')
    active_phases = {0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 19, 20, 22, 24, 25, 26, 28, 29}
    PK_P07_need(all((value == 0 for index, value in enumerate(phase_work) if index not in active_phases)), 'GRAPH_UNUSED_PHASE_WORK')
    body = document['body']
    PK_P07_keys(body, ('schema', 'stability', 'grid', 'path', 'work', 'masks'), 'GRAPH_BODY_SCHEMA')
    PK_P07_need(body['schema'] == PK_P07_GRAPH_BODY_SCHEMA, 'GRAPH_BODY_IDENTITY')
    stability = body['stability']
    stability_names = ('prefixGeometrySha256', 'liveBeforeVerified', 'liveAfterVerified', 'liveEffectsUnchanged', 'goalVerified', 'originalPathVerified')
    PK_P07_keys(stability, stability_names, 'GRAPH_STABILITY_SCHEMA')
    PK_P07_need(PK_P07_digest(stability['prefixGeometrySha256']) and all((type(stability[name]) is bool and stability[name] is True for name in stability_names[1:])), 'GRAPH_STABILITY_VALUES')
    pub_before, pub_after = (publisher['before'], publisher['after'])
    PK_P07_need(stability['prefixGeometrySha256'] == pub_before['geometrySha256'] == pub_after['geometrySha256'] == branch_row['prefixSha256'] and pub_before['geometryBytes'] == pub_after['geometryBytes'] == branch_row['prefixBytes'], 'GRAPH_PREFIX_GEOMETRY_JOIN')
    grid = body['grid']
    grid_names = ('width', 'height', 'cells', 'originX', 'originY', 'pitch', 'startCell', 'initialLayerMask', 'endCell', 'endLayer', 'goalUnionCellCount')
    PK_P07_keys(grid, grid_names, 'GRAPH_GRID_SCHEMA')
    pub_grid = publisher['grid']
    PK_P07_need(all((PK_P07_integer(grid[name], 1, PK_P07_LIMITS['cells']) for name in ('width', 'height', 'cells'))) and grid['cells'] == grid['width'] * grid['height'] and PK_P07_integer(grid['originX'], -1000000, 1000000) and PK_P07_integer(grid['originY'], -1000000, 1000000) and PK_P07_integer(grid['pitch'], 10, 10) and PK_P07_integer(grid['startCell'], 0, grid['cells'] - 1) and PK_P07_integer(grid['endCell'], 0, grid['cells'] - 1) and PK_P07_integer(grid['endLayer'], 0, 1) and PK_P07_integer(grid['goalUnionCellCount'], 1, grid['cells']) and ((grid['width'], grid['height'], grid['cells'], grid['originX'], grid['originY'], grid['pitch']) == (pub_grid['width'], pub_grid['height'], pub_grid['cells'], pub_grid['originX'], pub_grid['originY'], pub_grid['pitch'])) and (grid['initialLayerMask'] == source['initialLayerMask'] == 3), 'GRAPH_GRID_VALUES')
    nodes = publisher['path']['nodes']
    PK_P07_need(grid['startCell'] == nodes[0][0] and grid['endCell'] == nodes[-1][0] and (grid['endLayer'] == nodes[-1][1]), 'GRAPH_GRID_PATH_JOIN')
    masks = body['masks']
    PK_P07_keys(masks, ('topStepsHex', 'bottomStepsHex', 'goalFacesHex'), 'GRAPH_MASK_SCHEMA')
    for name in ('topStepsHex', 'bottomStepsHex'):
        value = masks[name]
        PK_P07_need(type(value) is str and len(value) == grid['cells'] and (PK_P07_re.fullmatch('[0-9a-f]{' + str(grid['cells']) + '}', value) is not None), 'GRAPH_STEP_MASK')
    goal_faces = masks['goalFacesHex']
    PK_P07_need(type(goal_faces) is str and len(goal_faces) == grid['cells'] and (PK_P07_re.fullmatch('[0-3]{' + str(grid['cells']) + '}', goal_faces) is not None), 'GRAPH_GOAL_MASK')
    PK_P07_need(PK_P07_digest(document['goalFacesSha256']) and document['goalFacesSha256'] == PK_P07_sha(goal_faces.encode('ascii')), 'GRAPH_GOAL_HASH')
    goal_values = [ord(value) - 48 for value in goal_faces]
    PK_P07_need(sum((value != 0 for value in goal_values)) == grid['goalUnionCellCount'] and sum((bool(value & 1) for value in goal_values)) == branch_row['snapshot']['goalsTop'] and (sum((bool(value & 2) for value in goal_values)) == branch_row['snapshot']['goalsBottom']) and goal_values[grid['endCell']] & 1 << grid['endLayer'], 'GRAPH_GOAL_BRANCH_JOIN')
    for mask in (masks['topStepsHex'], masks['bottomStepsHex']):
        for cell, character in enumerate(mask):
            flags = int(character, 16)
            x, y = (cell % grid['width'], cell // grid['width'])
            PK_P07_need(not (y == 0 and flags & 1) and (not (x + 1 == grid['width'] and flags & 2)) and (not (y + 1 == grid['height'] and flags & 4)) and (not (x == 0 and flags & 8)), 'GRAPH_STEP_BOUNDARY')
    graph_path = body['path']
    PK_P07_keys(graph_path, ('nodeCount', 'planarEdgeCount', 'transitionCount', 'tupleSha256'), 'GRAPH_PATH_SCHEMA')
    PK_P07_need(PK_P07_integer(graph_path['nodeCount'], 1, PK_P07_LIMITS['pathNodes']) and PK_P07_integer(graph_path['planarEdgeCount'], 0, PK_P07_LIMITS['pathNodes'] - 1) and PK_P07_integer(graph_path['transitionCount'], 0, PK_P07_LIMITS['transitionCap']) and PK_P07_digest(graph_path['tupleSha256']) and (graph_path['nodeCount'] == len(nodes)), 'GRAPH_PATH_VALUES')
    planar_edges = sum((left[1] == right[1] for left, right in zip(nodes, nodes[1:])))
    transitions = len(nodes) - 1 - planar_edges
    tuple_rows = []
    for index, node in enumerate(nodes):
        cell, layer, x, y, direction, vias, cost, heuristic, serial, key_value, parent = node
        tuple_rows.append(','.join((str(value) for value in (index, parent, cell, x, y, layer, direction, vias, cost, heuristic, serial, key_value))))
        if index:
            previous = nodes[index - 1]
            if layer == previous[1]:
                dx, dy = (x - previous[2], y - previous[3])
                bit = 1 if (dx, dy) == (0, -grid['pitch']) else 2 if (dx, dy) == (grid['pitch'], 0) else 4 if (dx, dy) == (0, grid['pitch']) else 8 if (dx, dy) == (-grid['pitch'], 0) else 0
                PK_P07_need(bit != 0 and int(masks['topStepsHex' if layer == 0 else 'bottomStepsHex'][previous[0]], 16) & bit, 'GRAPH_PATH_DIRECTED_EDGE')
    tuple_preimage = '\n'.join(tuple_rows).encode('ascii')
    PK_P07_need(graph_path['planarEdgeCount'] == planar_edges and graph_path['transitionCount'] == transitions and (transitions == 0) and (graph_path['tupleSha256'] == PK_P07_sha(tuple_preimage)), 'GRAPH_PATH_HASH_OR_COUNT')
    work = body['work']
    work_names = ('advanceCalls', 'workSteps', 'copyEntries', 'comparisonEntries', 'geometryBytes', 'effectUnits', 'ordinaryQueries', 'goalCells', 'reachedPadChecks', 'encodedCells', 'pathNodes', 'pathEdgesVerified', 'tupleBytes', 'serializedUtf16Units', 'activeMillis')
    PK_P07_keys(work, work_names, 'GRAPH_WORK_SCHEMA')
    PK_P07_need(PK_P07_integer(entry['elapsedToCloseNanos'], 1, PK_P07_LIMITS['operationNanos']) and all((PK_P07_integer(work[name]) for name in work_names)) and (work['advanceCalls'] > 0) and (work['workSteps'] > 0) and (work['copyEntries'] > 0) and (work['comparisonEntries'] > 0), 'GRAPH_WORK_VALUES')
    cursor_groups = [group for group in groups if group['graphCursorSteps'] > 0]
    cursor_steps = sum((group['graphCursorSteps'] for group in groups))
    PK_P07_need(work['advanceCalls'] == len(cursor_groups) and work['workSteps'] == cursor_steps == sum(phase_work) and (work['ordinaryQueries'] == 2 * (2 * grid['height'] * (grid['width'] - 1) + 2 * grid['width'] * (grid['height'] - 1))) and (work['goalCells'] == grid['cells']) and (work['encodedCells'] == 3 * grid['cells']) and (work['pathNodes'] == graph_path['nodeCount']) and (work['pathEdgesVerified'] == graph_path['nodeCount'] - 1) and (work['geometryBytes'] == 2 * pub_before['geometryBytes']) and (work['tupleBytes'] == len(tuple_preimage)) and (work['serializedUtf16Units'] <= 1000000) and (work['activeMillis'] <= entry['elapsedToCloseNanos'] // 1000000), 'GRAPH_WORK_CONSERVATION')
    lower_bound_names = ('copyEntries', 'comparisonEntries', 'geometryBytes', 'effectUnits', 'ordinaryQueries', 'goalCells', 'reachedPadChecks', 'pathNodes', 'pathEdgesVerified', 'tupleBytes', 'serializedUtf16Units')
    PK_P07_need(work['workSteps'] >= sum((work[name] for name in lower_bound_names)), 'GRAPH_WORK_LOWER_BOUND')
    PK_P07_need(work['advanceCalls'] == len(cursor_groups) and all((group['graphCursorSteps'] <= 32768 for group in cursor_groups)), 'GRAPH_CURSOR_GROUP_CONSERVATION')
    body_raw = PK_P07__top_level_value_bytes(raw, 'body')
    canonical_body = PK_P07__canonical_graph_body(body)
    PK_P07_need(body_raw == canonical_body and work['serializedUtf16Units'] == len(body_raw), 'GRAPH_BODY_CANONICAL_BYTES')
    integrity = document['integrity']
    integrity_names = ('nativeSourceBound', 'parentPaused', 'retainedSourceUnchanged', 'cursorSourcesReleased', 'producerSourcesReleased')
    PK_P07_keys(integrity, integrity_names, 'GRAPH_INTEGRITY_SCHEMA')
    PK_P07_need(all((type(integrity[name]) is bool and integrity[name] is True for name in integrity_names)), 'GRAPH_INTEGRITY')
    limitations = document['limitations']
    limitations_names = ('model', 'lengthEligibilityOnly', 'legalSearchHistory', 'publicationQualified', 'layoutQualification', 'routeAdoption', 'playable100', 'fullPhysicalProof')
    PK_P07_keys(limitations, limitations_names, 'GRAPH_LIMITATIONS_SCHEMA')
    PK_P07_need(limitations['model'] == 'DIRECTED_PER_FACE_ORDINARY_EDGES' and type(limitations['lengthEligibilityOnly']) is bool and (limitations['lengthEligibilityOnly'] is True) and (limitations['legalSearchHistory'] == 'NOT_RUN') and (type(limitations['publicationQualified']) is bool) and (limitations['publicationQualified'] is False) and (type(limitations['layoutQualification']) is bool) and (limitations['layoutQualification'] is False) and (type(limitations['routeAdoption']) is bool) and (limitations['routeAdoption'] is False) and (type(limitations['playable100']) is int) and (limitations['playable100'] == 0) and (limitations['fullPhysicalProof'] == 'NOT_RUN'), 'GRAPH_LIMITATIONS')
    if header['mode'] == 'AUTHORED':
        PK_P07_need(context['operation'] == 4 and context['attempt'] == 4 and (context['request'] == 0) and (context['pass'] == 1) and ((context['rootSeed'], context['placementSeed'], context['routingSeed']) == ('9007199254740993', '-6587958367974952701', '8445301674744508279')) and ((branch['ordinal'], branch['netVectorPosition'], branch['netId'], branch['startPadId'], branch['rootPadId'], branch['foundTerminalPadId'], branch['branchRecordGroup'], branch['reachedPadIds']) == (248, 28, 'OUT_F', 'RSH_F.1', 'CSTORE_F.2', 'CSTORE_F.2', 57, ['CSTORE_F.2'])) and (branch_row['snapshot']['terminalReason'] == 'FOUND') and ((source['target'], source['sourceKind'], source['policy'], source['initialLayerMask'], source['reachedPthPadIds']) == ('OUT_F', 'OUT_F_REJECTED_PUBLISHER_PATH', 'FULLER_TWO_LAYER', 3, [])) and ((grid['width'], grid['height'], grid['cells'], grid['originX'], grid['originY'], grid['pitch']) == (478, 309, 147702, 30, 30, 10)) and (pub_before == pub_after) and (len(pub_before['traces']) == 180) and (len(pub_before['holes']) == 48) and (pub_before['geometryBytes'] == 271606) and (pub_before['geometrySha256'] == 'cbc98315295693be7cddc31d0327f6f89e5819fedb2b4ad1d65a25e7decc2739') and ((graph_path['nodeCount'], graph_path['planarEdgeCount'], graph_path['transitionCount']) == (171, 170, 0)) and ((work['ordinaryQueries'], work['goalCells']) == (1178468, 147702)), 'GRAPH_AUTHORED_IDENTITY')
        start_pad = pads_by_id[branch['startPadId']]
        found_node = nodes[-1]
        path_length = sum((grid['pitch'] for left, right in zip(nodes, nodes[1:]) if left[1] == right[1]))
        direct = abs(start_pad['x'] - found_node[2]) + abs(start_pad['y'] - found_node[3])
        bends = 0
        previous_direction = 4
        for left, right in zip(nodes, nodes[1:]):
            if left[1] != right[1]:
                previous_direction = 4
            else:
                if previous_direction != 4 and right[4] != previous_direction:
                    bends += 1
                previous_direction = right[4]
        PK_P07_need((path_length, bends, direct, grid['endLayer']) == (1700, 4, 320, 1), 'GRAPH_AUTHORED_PATH_METRICS')
    else:
        PK_P07_need(header['mode'] == 'TINY_CANARY', 'GRAPH_FIXTURE_MODE')
    diagnostics = PK_P07_analyze_graph(document)
    serialized_bytes = len(raw) + sum((len(line) for _row, line, _decoded in graph_chunks)) + len(result_line)
    capture_hash_bytes = len(raw) + sum((len(line) for _row, line, _decoded in graph_chunks)) + len(result_line)
    return dict(document=document, payloadBytes=len(raw), payloadSha256=PK_P07_sha(raw), serializedBytes=serialized_bytes, captureHashBytes=capture_hash_bytes, diagnostics=diagnostics)

def PK_P07_validate_pad_census(pads, candidate):
    PK_P07_need([p['padId'] for p in pads] == sorted({p['padId'] for p in pads}) and len({p['netId'] for p in pads}) == candidate['netCount'] and (len({p['componentId'] for p in pads}) == candidate['componentCount']), 'PAD_CENSUS')

def PK_P07_validate_snapshot_shape(snapshot):
    PK_P07_keys(snapshot, PK_P07_SNAP_INTS + PK_P07_SNAP_BOOLS + PK_P07_SNAP_STRINGS, 'SNAPSHOT_SCHEMA')
    PK_P07_need(all((PK_P07_token(snapshot[n]) for n in ('netId', 'startPadId', 'rootPadId'))) and type(snapshot['terminalReason']) is str, 'SNAPSHOT_IDENTIFIERS')
    PK_P07_need(all((PK_P07_integer(snapshot[n], -2 ** 31, 2 ** 31 - 1) for n in PK_P07_SNAP_INTS)) and all((type(snapshot[n]) is bool for n in PK_P07_SNAP_BOOLS)), 'SNAPSHOT_TYPES')
    PK_P07_need(all((PK_P07_integer(snapshot[n], 0, 2 ** 31 - 1) for n in PK_P07_SNAP_INTS if n not in ('startX', 'startY', 'rootX', 'rootY', 'initialManhattanDistance', 'minimumExploredManhattanDistance'))) and PK_P07_integer(snapshot['initialManhattanDistance'], -1, 2 ** 31 - 1) and PK_P07_integer(snapshot['minimumExploredManhattanDistance'], -1, 2 ** 31 - 1), 'SNAPSHOT_COUNT_DOMAIN')

def PK_P07_audit_choices(branches, pads, arm):
    by_id = {p['padId']: p for p in pads}
    nets = {}
    for p in pads:
        nets.setdefault(p['netId'], []).append(p['padId'])
    states = {}
    seen = {}
    prior = None
    total = 0
    choices = []
    for row in branches:
        s = row['snapshot']
        PK_P07_validate_snapshot_shape(s)
        p = s['pass']
        net = s['netId']
        PK_P07_need(PK_P07_integer(p, 0, 2) and net in nets and (s['terminalReason'] in ('FOUND', 'NO_PATH', 'TOTAL_SEARCH_LIMIT', 'BRANCH_SEARCH_LIMIT')), 'BRANCH_TERMINAL')
        PK_P07_need(PK_P07_integer(s['gridWidth'], 1, 250000) and PK_P07_integer(s['gridHeight'], 1, 250000) and (s['gridWidth'] * s['gridHeight'] <= 250000) and PK_P07_integer(s['prefixViaCount'], 0, 48), 'BRANCH_GRID_HOLES')
        PK_P07_need(PK_P07_integer(s['branchExpanded'], 0, 100000) and PK_P07_integer(s['totalExpanded'], 0, 1000000) and PK_P07_integer(s['expandedTop']) and PK_P07_integer(s['expandedBottom']) and (s['expandedTop'] + s['expandedBottom'] == s['branchExpanded']), 'BRANCH_EXPANSIONS')
        total += s['branchExpanded']
        PK_P07_need(total == s['totalExpanded'], 'CUMULATIVE_EXPANSIONS')
        if prior:
            ps = prior['snapshot']
            PK_P07_need(p >= ps['pass'], 'PASS_ORDER')
            if p == ps['pass']:
                PK_P07_need(ps['terminalReason'] == 'FOUND', 'FOLLOWED_FAILED_BRANCH')
                old = states[p, ps['netId']]
                old['reached'].append(ps['startPadId'])
                if net != ps['netId']:
                    PK_P07_need(not old['remaining'], 'LEFT_UNFINISHED_NET')
        key = (p, net)
        if key not in states:
            PK_P07_need(net not in seen.setdefault(p, set()), 'NET_REVISIT')
            seen[p].add(net)
            ordered = sorted(nets[net])
            PK_P07_need(len(ordered) > 1, 'SINGLE_PAD_BRANCH')
            states[key] = {'reached': [ordered[0]], 'remaining': ordered[1:]}
        else:
            PK_P07_need(prior is not None and prior['snapshot']['pass'] == p and (prior['snapshot']['netId'] == net), 'NET_REVISIT')
        state = states[key]
        remaining = state['remaining']
        reached = state['reached']
        PK_P07_need(remaining and s['rootPadId'] == sorted(nets[net])[0] and (s['reachedPadCount'] == len(reached)), 'BRANCH_ROOT_REACHED')
        distances = {a: min((abs(by_id[a]['x'] - by_id[b]['x']) + abs(by_id[a]['y'] - by_id[b]['y']) for b in reached)) for a in remaining}
        nearest = min(remaining, key=lambda a: (distances[a], a))
        PK_P07_need(s['startPadId'] == nearest, 'INDEPENDENT_CHOICE')
        for which in ('start', 'root'):
            pad = by_id.get(s[which + 'PadId'])
            PK_P07_need(pad is not None and pad['netId'] == net and ((s[which + 'X'], s[which + 'Y']) == (pad['x'], pad['y'])), 'BRANCH_ENDPOINT')
        choices.append(dict(nearest=nearest, reached=tuple(reached), remaining=tuple(remaining)))
        remaining.remove(nearest)
        prior = row
    return (choices, total)

def PK_P07_validate_diagnostic_clones(events, branches, branch_positions, pads, groups, arm):
    PK_P07_need(arm != 0 or not events, 'DIAGNOSTIC_CLONE_PLAIN')
    pad_by_id = {row['padId']: row for row in pads}
    pads_by_net = {}
    for row in pads:
        pads_by_net.setdefault(row['netId'], set()).add(row['padId'])
    groups_by_id = {row['ordinal']: row for row in groups}
    seen_keys = set()
    matched = {i: [] for i in range(len(branches))}
    for position, row in events:
        key = (row['pass'], row['netId'], row['startPadId'], row['rootPadId'], row['faceIndex'])
        PK_P07_need(key not in seen_keys, 'DIAGNOSTIC_CLONE_DUPLICATE')
        seen_keys.add(key)
        PK_P07_need(row['netId'] in pads_by_net and row['startPadId'] in pad_by_id and (row['rootPadId'] in pad_by_id) and (pad_by_id[row['startPadId']]['netId'] == row['netId']) and (pad_by_id[row['rootPadId']]['netId'] == row['netId']), 'DIAGNOSTIC_CLONE_CENSUS')
        group = groups_by_id.get(row['group'])
        PK_P07_need(group is not None and group['phase'] == 'ADVANCE' and (group['p07Slices'] == 1) and (group['accounting'] == 'MEASURED') and (row['ownerSteps'] <= group['p07Steps']), 'DIAGNOSTIC_CLONE_GROUP')
        candidates = []
        for i, branch in enumerate(branches):
            snapshot = branch['snapshot']
            if (snapshot['pass'], snapshot['netId'], snapshot['startPadId'], snapshot['rootPadId'], snapshot['prefixViaCount']) == (row['pass'], row['netId'], row['startPadId'], row['rootPadId'], row['prefixViaCount']):
                candidates.append(i)
        PK_P07_need(len(candidates) == 1, 'DIAGNOSTIC_CLONE_BRANCH_MATCH')
        branch_index = candidates[0]
        PK_P07_need(position < branch_positions[branch_index], 'DIAGNOSTIC_CLONE_ORDER')
        matched[branch_index].append((position, row))
    for i, branch in enumerate(branches):
        snapshot = branch['snapshot']
        branch_events = matched[i]
        if snapshot['relaxedBoundStarted']:
            PK_P07_need(snapshot['relaxedBoundComplete'] is True and all((snapshot[name] is True for name in PK_P07_DIAGNOSTIC_BOUND_PROOFS)), 'DIAGNOSTIC_CLONE_PROOF')
        expected = snapshot['relaxedBoundStarted'] and snapshot['prefixViaCount'] < PK_P07_LIMITS['holeCap']
        if expected:
            PK_P07_need([row['faceIndex'] for _, row in branch_events] == [0, 1] and sum((row['ownerSteps'] for _, row in branch_events)) == 2, 'DIAGNOSTIC_CLONE_PAIR')
        else:
            PK_P07_need(not branch_events, 'DIAGNOSTIC_CLONE_UNEXPECTED')
    PK_P07_need(len(events) <= 2 * 3 * (len(pads) - len(pads_by_net)), 'DIAGNOSTIC_CLONE_CAP')

def PK_P07_validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind):
    PK_P07_need(raw and len(raw) <= PK_P07_LIMITS['receiptBytes'] and raw.endswith(b'\n') and (b'\r' not in raw), 'RECEIPT_ENCODING_SIZE')
    lines = raw.splitlines(keepends=True)
    PK_P07_need(all((len(x) <= PK_P07_LIMITS['lineBytes'] and x != b'\n' for x in lines)), 'LINE_SIZE_EMPTY')
    rows = [PK_P07_strict_json(x[:-1]) for x in lines]
    h = rows[0]
    PK_P07_keys(h, ('record', 'group', 'schema', 'runId', 'mode', 'operation', 'arm', 'sourceManifestSha256', 'wireSha256', 'candidate', 'limits'), 'HEADER_SCHEMA')
    PK_P07_need(h['record'] == 'HEADER' and PK_P07_integer(h['group'], 0, 0) and (h['schema'] == PK_P07_SCHEMA) and (h['runId'] == run_id) and PK_P07_integer(h['operation'], 0, 4) and (h['operation'] == arm) and (h['sourceManifestSha256'] == source_sha) and (h['wireSha256'] == wire_sha) and (h['limits'] == PK_P07_LIMITS), 'HEADER_IDENTITY')
    PK_P07_keys(h['arm'], ('id', 'api', 'strategy', 'netOrderStrategy', 'observed', 'publisherCapture', 'graphCapture'), 'ARM_SCHEMA')
    PK_P07_need(tuple((h['arm'][x] for x in ('id', 'api', 'strategy', 'netOrderStrategy', 'observed', 'publisherCapture'))) == PK_P07_ARMS[arm] and type(h['arm']['observed']) is bool and (type(h['arm']['publisherCapture']) is bool) and (type(h['arm']['graphCapture']) is bool), 'ARM_IDENTITY')
    graph_capture_enabled = h['arm']['graphCapture']
    if arm != 4 or kind != 'READER_FIXTURE':
        PK_P07_need(graph_capture_enabled is (arm == 4 and kind == 'AUTHORED'), 'ARM_GRAPH_CAPTURE')
    c = h['candidate']
    PK_P07_keys(c, ('rootSeed', 'placementSeed', 'routingSeed', 'variant', 'layoutVersion', 'dependencyEpoch', 'componentCount', 'padCount', 'netCount', 'traceWidthProfile'), 'CANDIDATE_SCHEMA')
    for name in ('rootSeed', 'placementSeed', 'routingSeed'):
        PK_P07_need(type(c[name]) is str and PK_P07_re.fullmatch('0|-?[1-9][0-9]*', c[name]) is not None and (-2 ** 63 <= int(c[name]) < 2 ** 63), 'SEED_TRANSPORT')
    PK_P07_need(c['layoutVersion'] == 26 and c['dependencyEpoch'] == 'tsj-generation-dependencies-v28' and (c['traceWidthProfile'] == 'ROLE_WIDTHS') and PK_P07_integer(c['padCount'], 2, 1024) and PK_P07_integer(c['netCount'], 1, c['padCount'] - 1) and PK_P07_integer(c['componentCount'], 1, c['padCount']), 'CANDIDATE_VALUES')
    if kind == 'AUTHORED':
        PK_P07_need(h['mode'] == 'AUTHORED' and tuple((c[x] for x in ('rootSeed', 'placementSeed', 'routingSeed'))) == ('9007199254740993', '-6587958367974952701', '8445301674744508279') and (c['variant'] == 'SEPARATE') and (c['componentCount'] == 100) and (c['padCount'] == 246), 'AUTHORED_CANDIDATE')
    else:
        PK_P07_need(h['mode'] == 'TINY_CANARY' and c['variant'] == 'TINY' and (tuple((c[x] for x in ('rootSeed', 'placementSeed', 'routingSeed'))) == ('42', '5526715536693200536', '4493419427463069983')), 'TINY_CANDIDATE')
    pads = []
    branches = []
    groups = []
    result = None
    current_group = 0
    group_bytes = 0
    group_callbacks = 0
    group_pads = 0
    group_diagnostic_clones = 0
    group_net_orders = 0
    group_publisher_chunks = 0
    group_publisher_results = 0
    branch_positions = []
    diagnostic_events = []
    net_order_rows = []
    net_order_positions = []
    publisher_chunks = []
    publisher_result = None
    publisher_result_line = None
    publisher_payload_bytes = 0
    graph_chunks = []
    graph_result = None
    graph_result_line = None
    graph_payload_bytes = 0
    publisher_output_started = False
    publisher_capture_closed = False
    graph_output_started = False
    graph_capture_closed = False
    graph_cursor_started = False
    group_graph_chunks = 0
    group_graph_results = 0
    branch_seen = False
    report_seen = False
    report_closed = False
    pass_one_seen = False
    for line_index, (row, line) in enumerate(zip(rows[:-1], lines[:-1])):
        PK_P07_need(not report_closed, 'TRAILING_REPORT_RECORD')
        PK_P07_need(type(row) is dict and type(row.get('record')) is str, 'RECORD_TYPE')
        typ = row['record']
        if result is not None:
            PK_P07_need(typ == 'GROUP', 'CAPTURE_AFTER_RESULT')
        source_record = typ in ('PAD', 'BRANCH', 'NET_ORDER', 'DIAGNOSTIC_CLONE')
        if graph_capture_enabled and graph_cursor_started and (not graph_capture_closed):
            PK_P07_need(not source_record, 'GRAPH_CAPTURE_SOURCE_INTERLEAVING')
        capture_closed = graph_capture_closed if graph_capture_enabled else publisher_capture_closed
        if (publisher_output_started or publisher_result is not None) and (not capture_closed):
            allowed_output = ('PUB_CHUNK', 'PUB_RESULT', 'GRAPH_CHUNK', 'GRAPH_RESULT', 'RESULT', 'GROUP') if graph_capture_enabled else ('PUB_CHUNK', 'PUB_RESULT', 'RESULT', 'GROUP')
            PK_P07_need(typ in allowed_output, 'CAPTURE_OUTPUT_INTERLEAVING')
        if typ == 'HEADER':
            PK_P07_need(row is h, 'EXTRA_HEADER')
        elif typ == 'PAD':
            PK_P07_keys(row, ('record', 'group', 'padId', 'netId', 'componentId', 'x', 'y', 'role'), 'PAD_SCHEMA')
            PK_P07_need(not branch_seen and (not report_seen) and all((PK_P07_token(row[n]) for n in ('padId', 'netId', 'componentId', 'role'))) and PK_P07_integer(row['x'], -1000000, 1000000) and PK_P07_integer(row['y'], -1000000, 1000000), 'PAD_VALUES_ORDER')
            pads.append(row)
            group_pads += 1
        elif typ == 'BRANCH':
            PK_P07_keys(row, ('record', 'operation', 'ordinal', 'group', 'prefixSha256', 'prefixBytes', 'snapshot'), 'BRANCH_SCHEMA')
            PK_P07_need(not report_seen and arm != 0 and PK_P07_integer(row['operation'], 0, 4) and (row['operation'] == arm) and PK_P07_integer(row['ordinal']) and (row['ordinal'] == len(branches)) and PK_P07_digest(row['prefixSha256']) and PK_P07_integer(row['prefixBytes'], 1, PK_P07_LIMITS['prefixBytes']), 'BRANCH_IDENTITY')
            PK_P07_validate_snapshot_shape(row['snapshot'])
            branches.append(row)
            branch_positions.append(line_index)
            group_callbacks += 1
            branch_seen = True
            if type(row.get('snapshot')) is dict and row['snapshot'].get('pass') == 1:
                pass_one_seen = True
                PK_P07_need(len(net_order_rows) == 1 and net_order_positions[0] < line_index, 'NET_ORDER_MISSING_OR_LATE')
        elif typ == 'NET_ORDER':
            PK_P07_keys(row, PK_P07_NET_ORDER_FIELDS, 'NET_ORDER_SCHEMA')
            PK_P07_need(not net_order_rows, 'NET_ORDER_DUPLICATE')
            PK_P07_need(not report_seen and arm in (1, 2, 3, 4) and (not pass_one_seen) and (len(pads) == c['padCount']), 'NET_ORDER_POSITION')
            PK_P07_validate_pad_census(pads, c)
            PK_P07_need(PK_P07_integer(row['operation'], 1, 4) and row['operation'] == arm and PK_P07_integer(row['group'], 0, 639) and (row['group'] == current_group) and PK_P07_integer(row['pass'], 1, 1) and (row['strategy'] == PK_P07_ARMS[arm][3]), 'NET_ORDER_IDENTITY')
            PK_P07_need(row['promotedNet'] is None or PK_P07_token(row['promotedNet']), 'NET_ORDER_PROMOTION_TYPE')
            PK_P07_need(PK_P07_integer(row['completedOrderings'], 1, 1) and PK_P07_integer(row['yieldedOrderings'], 0, 1) and PK_P07_digest(row['prefixSha256']) and PK_P07_integer(row['prefixBytes'], 1, PK_P07_LIMITS['prefixBytes']), 'NET_ORDER_VALUES')
            net_ids = row['netIds']
            PK_P07_need(type(net_ids) is list and all((PK_P07_token(net) for net in net_ids)) and (len(net_ids) == len(set(net_ids))) and (set(net_ids) == {pad['netId'] for pad in pads}), 'NET_ORDER_CENSUS')
            net_order_rows.append(row)
            net_order_positions.append(line_index)
            group_net_orders += 1
        elif typ == 'PUB_CHUNK':
            PK_P07_keys(row, PK_P07_PUB_CHUNK_FIELDS, 'PUB_CHUNK_SCHEMA')
            PK_P07_need(arm == 4 and result is None and (publisher_result is None) and (not graph_output_started) and (row['operation'] == 4) and (row['targetId'] == PK_P07_PUBLISHER_TARGET_ID) and PK_P07_integer(row['group'], 0, 639) and (row['group'] == current_group) and PK_P07_integer(row['index'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_integer(row['offset'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_integer(row['bytes'], 1, PK_P07_LIMITS['publisherChunkBytes']) and (type(row['data']) is str), 'PUB_CHUNK_IDENTITY')
            PK_P07_need(row['index'] == len(publisher_chunks) and row['offset'] == publisher_payload_bytes, 'PUB_CHUNK_ORDER')
            try:
                encoded = row['data'].encode('ascii')
                decoded = PK_P07_base64.b64decode(encoded, validate=True)
            except (UnicodeEncodeError, ValueError):
                raise ValueError('PUB_CHUNK_BASE64')
            PK_P07_need(PK_P07_base64.b64encode(decoded).decode('ascii') == row['data'] and len(decoded) == row['bytes'], 'PUB_CHUNK_BASE64')
            publisher_payload_bytes += len(decoded)
            PK_P07_need(publisher_payload_bytes <= PK_P07_LIMITS['publisherPayloadBytes'], 'PUB_PAYLOAD_LIMIT')
            publisher_chunks.append((row, line, decoded))
            publisher_output_started = True
            group_publisher_chunks += 1
        elif typ == 'PUB_RESULT':
            PK_P07_keys(row, PK_P07_PUB_RESULT_FIELDS, 'PUB_RESULT_SCHEMA')
            PK_P07_need(arm == 4 and result is None and (publisher_result is None) and (not graph_output_started) and (row['operation'] == 4) and (row['targetId'] == PK_P07_PUBLISHER_TARGET_ID) and PK_P07_integer(row['group'], 0, 639) and (row['group'] == current_group) and PK_P07_integer(row['payloadBytes'], 1, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_digest(row['payloadSha256']) and PK_P07_integer(row['chunkCount'], 1, PK_P07_LIMITS['publisherPayloadBytes']), 'PUB_RESULT_IDENTITY')
            PK_P07_need(publisher_chunks and row['chunkCount'] == len(publisher_chunks) and (row['payloadBytes'] == publisher_payload_bytes), 'PUB_RESULT_COUNTS')
            publisher_result = row
            publisher_result_line = line
            group_publisher_results += 1
        elif typ == 'GRAPH_CHUNK':
            PK_P07_keys(row, PK_P07_GRAPH_CHUNK_FIELDS, 'GRAPH_CHUNK_SCHEMA')
            PK_P07_need(graph_capture_enabled and arm == 4 and (result is None) and (publisher_result is not None) and (graph_result is None) and (row['record'] == 'GRAPH_CHUNK') and PK_P07_integer(row['operation'], 4, 4) and (row['targetId'] == PK_P07_GRAPH_TARGET_ID) and PK_P07_integer(row['group'], 0, 639) and (row['group'] == current_group) and PK_P07_integer(row['index'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_integer(row['offset'], 0, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_integer(row['bytes'], 1, PK_P07_LIMITS['publisherChunkBytes']) and (type(row['data']) is str), 'GRAPH_CHUNK_IDENTITY')
            PK_P07_need(row['index'] == len(graph_chunks) and row['offset'] == graph_payload_bytes, 'GRAPH_CHUNK_ORDER')
            try:
                encoded = row['data'].encode('ascii')
                decoded = PK_P07_base64.b64decode(encoded, validate=True)
            except (UnicodeEncodeError, ValueError):
                raise ValueError('GRAPH_CHUNK_BASE64')
            PK_P07_need(PK_P07_base64.b64encode(decoded).decode('ascii') == row['data'] and len(decoded) == row['bytes'], 'GRAPH_CHUNK_BASE64')
            graph_payload_bytes += len(decoded)
            PK_P07_need(graph_payload_bytes <= PK_P07_LIMITS['publisherPayloadBytes'], 'GRAPH_PAYLOAD_LIMIT')
            graph_chunks.append((row, line, decoded))
            group_graph_chunks += 1
            graph_output_started = True
        elif typ == 'GRAPH_RESULT':
            PK_P07_keys(row, PK_P07_GRAPH_RESULT_FIELDS, 'GRAPH_RESULT_SCHEMA')
            PK_P07_need(graph_capture_enabled and arm == 4 and (result is None) and (publisher_result is not None) and (graph_result is None) and (row['record'] == 'GRAPH_RESULT') and PK_P07_integer(row['operation'], 4, 4) and (row['targetId'] == PK_P07_GRAPH_TARGET_ID) and PK_P07_integer(row['group'], 0, 639) and (row['group'] == current_group) and PK_P07_integer(row['payloadBytes'], 1, PK_P07_LIMITS['publisherPayloadBytes']) and PK_P07_digest(row['payloadSha256']) and PK_P07_integer(row['chunkCount'], 1, PK_P07_LIMITS['publisherPayloadBytes']), 'GRAPH_RESULT_IDENTITY')
            PK_P07_need(graph_chunks and row['chunkCount'] == len(graph_chunks) and (row['payloadBytes'] == graph_payload_bytes), 'GRAPH_RESULT_COUNTS')
            graph_result = row
            graph_result_line = line
            group_graph_results += 1
            graph_output_started = True
        elif typ == 'DIAGNOSTIC_CLONE':
            PK_P07_keys(row, PK_P07_DIAGNOSTIC_CLONE_FIELDS, 'DIAGNOSTIC_CLONE_SCHEMA')
            PK_P07_need(arm in (1, 2, 3, 4) and PK_P07_integer(row['operation'], 1, 4) and (row['operation'] == arm) and PK_P07_integer(row['ordinal']) and (row['ordinal'] == len(diagnostic_events)) and PK_P07_integer(row['pass'], 0, 2) and all((PK_P07_token(row[name]) for name in ('netId', 'startPadId', 'rootPadId'))), 'DIAGNOSTIC_CLONE_IDENTITY')
            PK_P07_need(PK_P07_integer(row['faceIndex'], 0, 1), 'DIAGNOSTIC_CLONE_FACE')
            PK_P07_need(PK_P07_integer(row['prefixViaCount'], 0, PK_P07_LIMITS['holeCap'] - 1), 'DIAGNOSTIC_CLONE_CAPACITY')
            PK_P07_need(PK_P07_integer(row['ownerSteps']) and row['ownerSteps'] == 1, 'DIAGNOSTIC_CLONE_STEPS')
            diagnostic_events.append((line_index, row))
            group_diagnostic_clones += 1
        elif typ == 'RESULT':
            PK_P07_keys(row, ('record', 'operation', 'group', 'proof', 'route', 'context', 'receipt', 'outcome', 'winner', 'physical', 'logicalWork', 'boundary'), 'RESULT_SCHEMA')
            PK_P07_need(result is None and PK_P07_integer(row['operation'], 0, 4) and (row['operation'] == arm) and (row['proof'] == 'COMPLETE'), 'RESULT_IDENTITY')
            PK_P07_need(arm == 4 and publisher_result is not None and (graph_capture_enabled and graph_result is not None or (not graph_capture_enabled and (not graph_chunks) and (graph_result is None))) or (arm != 4 and (not publisher_chunks) and (publisher_result is None) and (not graph_chunks) and (graph_result is None)), 'CAPTURE_RESULT_BEFORE_REQUEST_RESULT')
            result = row
            report_seen = True
        elif typ == 'GROUP':
            PK_P07_keys(row, PK_P07_GROUP_FIELDS, 'GROUP_SCHEMA')
            PK_P07_need(PK_P07_integer(row['operation'], 0, 4) and row['operation'] == arm and PK_P07_integer(row['ordinal'], 0, 639) and (row['ordinal'] == current_group) and (row['phase'] in ('SETUP', 'ADVANCE', 'PHYSICAL', 'CAPTURE', 'REPORT')), 'GROUP_IDENTITY')
            for n in ('elapsedBeforeOwnLineNanos', 'observerChecks', 'callbacks', 'censusRows', 'netOrderRows', 'hashInputBytes', 'serializedBeforeOwnLineBytes', 'diagnosticCloneSteps', 'publisherChunkRows', 'publisherResultRows', 'graphChunkRows', 'graphResultRows', 'graphCursorSteps', 'diagnosticCopiedEntries', 'diagnosticSerializedBytes', 'diagnosticHashBytes', 'diagnosticUnits'):
                PK_P07_need(PK_P07_integer(row[n]), 'GROUP_NUMBER')
            PK_P07_need(row['elapsedBeforeOwnLineNanos'] <= PK_P07_LIMITS['groupNanos'] and PK_P07_integer(row['generatorAdvances'], 0, 1) and PK_P07_integer(row['p07Slices'], 0, 1) and (row['p07Slices'] <= row['generatorAdvances']) and (row['graphCursorSteps'] <= 32768), 'GROUP_LIMIT')
            PK_P07_need(row['generatorAdvances'] == (1 if row['phase'] == 'ADVANCE' else 0), 'GROUP_PHASE_WORK')
            if row['phase'] == 'CAPTURE':
                PK_P07_need(row['generatorAdvances'] == 0 and row['p07Slices'] == 0 and (row['accounting'] == 'NOT_APPLICABLE') and (row['p07Steps'] is None) and (row['observerChecks'] == 0) and (row['callbacks'] == 0) and (row['censusRows'] == 0) and (row['netOrderRows'] == 0) and (row['diagnosticCloneSteps'] == 0), 'CAPTURE_OWNER_WORK')
                PK_P07_need(arm == 4 and row['diagnosticUnits'] <= 32768, 'CAPTURE_DIAGNOSTIC_LIMIT')
                PK_P07_need(row['graphChunkRows'] == 0 and row['graphResultRows'] == 0 and (row['graphCursorSteps'] == 0) or (graph_capture_enabled and arm == 4), 'GRAPH_CAPTURE_GROUP')
                if row['graphCursorSteps']:
                    PK_P07_need(group_publisher_chunks == 0 and group_publisher_results == 0 and (group_graph_chunks == 0) and (group_graph_results == 0), 'GRAPH_CURSOR_OUTPUT_ORDER')
            elif row['p07Slices']:
                PK_P07_need(row['accounting'] == 'MEASURED' and PK_P07_integer(row['p07Steps'], 0, 32768) and (row['observerChecks'] >= 1), 'P07_ACCOUNTING')
            else:
                PK_P07_need(row['accounting'] == 'NOT_APPLICABLE' and row['p07Steps'] is None, 'P07_ABSENCE')
            PK_P07_need(row['callbacks'] == group_callbacks and row['censusRows'] == group_pads and (row['netOrderRows'] == group_net_orders) and (row['publisherChunkRows'] == group_publisher_chunks) and (row['publisherResultRows'] == group_publisher_results) and (row['graphChunkRows'] == group_graph_chunks) and (row['graphResultRows'] == group_graph_results) and (row['serializedBeforeOwnLineBytes'] == group_bytes), 'GROUP_RECORD_COUNTS')
            PK_P07_need(row['diagnosticUnits'] == row['graphCursorSteps'] + row['diagnosticCopiedEntries'] + row['diagnosticSerializedBytes'] + row['diagnosticHashBytes'], 'DIAGNOSTIC_UNIT_SUM')
            if arm != 4:
                PK_P07_need(row['publisherChunkRows'] == 0 and row['publisherResultRows'] == 0 and (row['graphChunkRows'] == 0) and (row['graphResultRows'] == 0) and (row['graphCursorSteps'] == 0) and (row['diagnosticCopiedEntries'] == 0) and (row['diagnosticSerializedBytes'] == 0) and (row['diagnosticHashBytes'] == 0) and (row['diagnosticUnits'] == 0), 'PUB_NON_CAPTURE_ACCOUNTING')
            if row['phase'] == 'ADVANCE':
                PK_P07_need(row['diagnosticCopiedEntries'] <= PK_P07_LIMITS['snapshotEntries'] and row['diagnosticSerializedBytes'] == 0 and (row['diagnosticHashBytes'] == 0), 'ADVANCE_DIAGNOSTIC_SCOPE')
            elif row['phase'] == 'CAPTURE':
                PK_P07_need(row['diagnosticUnits'] <= 32768, 'CAPTURE_DIAGNOSTIC_LIMIT')
            else:
                PK_P07_need(row['diagnosticCopiedEntries'] == 0 and row['graphChunkRows'] == 0 and (row['graphResultRows'] == 0) and (row['graphCursorSteps'] == 0) and (row['diagnosticSerializedBytes'] == 0) and (row['diagnosticHashBytes'] == 0) and (row['diagnosticUnits'] == 0), 'DIAGNOSTIC_PHASE')
            if row['publisherChunkRows'] or row['publisherResultRows']:
                PK_P07_need(row['phase'] == 'CAPTURE', 'PUB_CAPTURE_PHASE')
            if row['graphChunkRows'] or row['graphResultRows'] or row['graphCursorSteps']:
                PK_P07_need(graph_capture_enabled and arm == 4 and (row['phase'] == 'CAPTURE'), 'GRAPH_CAPTURE_PHASE')
            if row['graphCursorSteps']:
                PK_P07_need(not publisher_output_started and (not graph_output_started), 'GRAPH_CURSOR_RESTART')
            PK_P07_need(row['diagnosticCloneSteps'] == group_diagnostic_clones, 'DIAGNOSTIC_CLONE_GROUP_COUNT')
            PK_P07_need(row['diagnosticCloneSteps'] <= (row['p07Steps'] or 0), 'DIAGNOSTIC_CLONE_STEP_CAP')
            if group_diagnostic_clones:
                PK_P07_need(row['phase'] == 'ADVANCE' and row['p07Slices'] == 1 and (row['accounting'] == 'MEASURED'), 'DIAGNOSTIC_CLONE_GROUP')
            if group_callbacks or group_pads or group_net_orders:
                PK_P07_need(row['phase'] == 'ADVANCE' and row['p07Slices'] == 1, 'LIVE_OWNER_OBSERVATION_GROUP')
            PK_P07_need(row['hashInputBytes'] >= sum((b['prefixBytes'] for b in branches if b['group'] == current_group)) + sum((order['prefixBytes'] for order in net_order_rows if order['group'] == current_group)) + row['diagnosticHashBytes'], 'GROUP_HASH_BYTES')
            if report_seen:
                PK_P07_need(row['phase'] == 'REPORT', 'FINAL_GROUP_PHASE')
                report_closed = True
            groups.append(row)
            current_group += 1
            group_bytes = 0
            group_callbacks = 0
            group_pads = 0
            group_diagnostic_clones = 0
            group_net_orders = 0
            group_publisher_chunks = 0
            group_publisher_results = 0
            group_graph_chunks = 0
            group_graph_results = 0
            if row['graphCursorSteps'] > 0:
                graph_cursor_started = True
            if row['publisherResultRows'] > 0:
                publisher_capture_closed = True
            if row['graphResultRows'] > 0:
                graph_capture_closed = True
            PK_P07_need(current_group <= 640, 'GROUP_COUNT')
            continue
        else:
            raise ValueError('UNEXPECTED_RECORD')
        PK_P07_need(PK_P07_integer(row.get('group'), 0, 639) and row['group'] == current_group, 'RECORD_GROUP')
        group_bytes += len(line)
    f = rows[-1]
    PK_P07_keys(f, ('record', 'group', 'operation', 'complete', 'padCount', 'branchCount', 'groupCount'), 'FOOTER_SCHEMA')
    PK_P07_need(result is not None and groups and (groups[-1]['phase'] == 'REPORT') and (group_bytes == 0) and (f['record'] == 'FOOTER') and PK_P07_integer(f['operation'], 0, 4) and (f['operation'] == arm) and PK_P07_integer(f['group'], 0, 639) and (f['group'] == len(groups) - 1) and (f['complete'] is True) and all((PK_P07_integer(f[n]) for n in ('padCount', 'branchCount', 'groupCount'))) and (f['padCount'] == len(pads) == c['padCount']) and (f['branchCount'] == len(branches)) and (f['groupCount'] == len(groups)), 'FOOTER_CENSUS')
    PK_P07_validate_pad_census(pads, c)
    PK_P07_need(all((len({p['role'] for p in pads if p['netId'] == net}) == 1 for net in {p['netId'] for p in pads})), 'NET_ROLE_CENSUS')
    PK_P07_need(len(branches) <= 3 * (len(pads) - c['netCount']), 'BRANCH_CAP')
    PK_P07_need(bool(net_order_rows) == pass_one_seen, 'NET_ORDER_PRESENCE')
    context = result['context']
    PK_P07_keys(context, ('requestIndex', 'selectedAttempt', 'eligibleAttempts', 'pristineSha256', 'pristineBytes', 'padCensusSha256', 'boardSha256'), 'CONTEXT_SCHEMA')
    eligible = context['eligibleAttempts']
    PK_P07_need(PK_P07_integer(context['requestIndex'], 0, 0) and PK_P07_integer(context['selectedAttempt'], 0, 5) and (type(eligible) is list) and (1 <= len(eligible) <= 6) and all((PK_P07_integer(x, 0, 5) for x in eligible)) and (len(set(eligible)) == len(eligible)) and (eligible[0] == context['selectedAttempt']) and PK_P07_digest(context['pristineSha256']) and PK_P07_integer(context['pristineBytes'], 1, PK_P07_LIMITS['prefixBytes']) and (context['padCensusSha256'] == PK_P07_sha(PK_P07_pad_bytes(pads))) and PK_P07_digest(context['boardSha256']), 'CONTEXT_VALUES')
    if kind == 'AUTHORED':
        PK_P07_need(context['selectedAttempt'] == 4, 'ACTUAL_ATTEMPT')
    r = result['receipt']
    PK_P07_validate_receipt(r)
    choices = []
    if arm:
        choices, total = PK_P07_audit_choices(branches, pads, arm)
        PK_P07_need(total == r['expansions'], 'RECEIPT_EXPANSION_SUM')
        total_limit_rows = [i for i, b in enumerate(branches) if b['snapshot']['terminalReason'] == 'TOTAL_SEARCH_LIMIT']
        if total_limit_rows:
            PK_P07_need(total_limit_rows == [len(branches) - 1] and r['termination'] == 'TOTAL_SEARCH_LIMIT' and (r['comparisonComplete'] is False), 'TOTAL_LIMIT_CONTINUATION')
        PK_P07_need({b['snapshot']['pass'] for b in branches} == set(range(r['startedOrderings'])), 'MISSING_ORDERING_BRANCH_PROOF')
    net_order = None
    net_order_audit = None
    if net_order_rows:
        net_order = net_order_rows[0]
        PK_P07_need(net_order_positions[0] > max((i for i, row in enumerate(rows) if row['record'] == 'PAD')), 'NET_ORDER_AFTER_CENSUS')
        pass_zero_positions = [position for position, branch in zip(branch_positions, branches) if type(branch.get('snapshot')) is dict and branch['snapshot'].get('pass') == 0]
        PK_P07_need(pass_zero_positions and net_order_positions[0] > max(pass_zero_positions), 'NET_ORDER_AFTER_PASS0')
        PK_P07_need(net_order['prefixSha256'] == context['pristineSha256'] and net_order['prefixBytes'] == context['pristineBytes'], 'NET_ORDER_PRISTINE_PREFIX')
        PK_P07_validate_net_order_promotion(net_order, branches, c['padCount'], c['netCount'])
        net_order_audit = PK_P07_audit_net_order(net_order, pads, branches, arm)
    PK_P07_validate_diagnostic_clones(diagnostic_events, branches, branch_positions, pads, groups, arm)
    diagnostic_clone_steps = sum((row['ownerSteps'] for _, row in diagnostic_events))
    required_hash_bytes = sum((b['prefixBytes'] for b in branches)) + sum((order['prefixBytes'] for order in net_order_rows)) + len(PK_P07_pad_bytes(pads)) + context['pristineBytes']
    boundary = result['boundary']
    PK_P07_keys(boundary, ('requestCount', 'outcomeCount', 'routeRetired', 'candidateRetired', 'generatorComplete', 'eventObserved', 'successorAdvanceCalls'), 'BOUNDARY_SCHEMA')
    PK_P07_need(boundary == dict(requestCount=1, outcomeCount=1, routeRetired=True, candidateRetired=True, generatorComplete=False, eventObserved=arm != 0, successorAdvanceCalls=0), 'REQUEST_BOUNDARY')
    PK_P07_need(all((type(boundary[n]) is bool for n in ('routeRetired', 'candidateRetired', 'generatorComplete', 'eventObserved'))) and all((PK_P07_integer(boundary[n]) for n in ('requestCount', 'outcomeCount', 'successorAdvanceCalls'))), 'REQUEST_BOUNDARY_TYPES')
    PK_P07_need(r['attempt'] == context['selectedAttempt'] and type(result['outcome']) is str and (0 < len(result['outcome'].encode()) <= 2048), 'RESULT_OUTCOME')
    physical = result['physical']
    PK_P07_keys(physical, ('connectivity', 'clearanceKeepout', 'layeredRaster', 'access', 'functional'), 'PHYSICAL_SCHEMA')
    if result['winner'] is None:
        PK_P07_need(result['route'] == 'TYPED_REQUEST_REJECTION' and r['finalizedOrderings'] == 0 and (r['selectedOrdering'] == -1) and all((x == 'NOT_RUN' for x in physical.values())), 'REJECTED_DISPOSITION')
    else:
        w = result['winner']
        PK_P07_keys(w, ('geometrySha256', 'geometryBytes', 'attempt', 'ordering', 'quality'), 'WINNER_SCHEMA')
        PK_P07_need(result['route'] == 'REQUEST_FINALIZED' and PK_P07_digest(w['geometrySha256']) and PK_P07_integer(w['geometryBytes'], 1, PK_P07_LIMITS['prefixBytes']) and PK_P07_integer(w['attempt'], 0, 5) and (w['attempt'] == r['attempt']) and PK_P07_integer(w['ordering'], 0, 2) and (w['ordering'] == r['selectedOrdering']) and (r['finalizedOrderings'] > 0) and (type(w['quality']) is str) and (0 < len(w['quality'].encode()) <= 4096) and all((physical[x] == 'PASS' for x in ('connectivity', 'clearanceKeepout', 'layeredRaster'))) and (physical['access'] == physical['functional'] == 'NOT_RUN'), 'FINALIZED_DISPOSITION')
        required_hash_bytes += w['geometryBytes']
    publisher_capture = None
    graph_capture = None
    if arm == 4:
        PK_P07_need(publisher_result is not None and publisher_chunks, 'PUB_CAPTURE_MISSING')
        payload = b''.join((decoded for _row, _line, decoded in publisher_chunks))
        publisher_capture = PK_P07__validate_publisher_document(payload, [(row, line) for row, line, _decoded in publisher_chunks], publisher_result, publisher_result_line, arm, source_sha, h, result, branches, net_order, groups, pads)
        checkpoints = publisher_capture['document']['checkpoints']
        terminal_group = checkpoints['publisherTerminal']['group']
        result_group = checkpoints['orderingResult']['group']
        PK_P07_need(all((row['group'] > terminal_group for row, _line, _decoded in publisher_chunks)), 'PUB_CHUNK_SOURCE_ORDER')
        PK_P07_need(publisher_result['group'] > result_group, 'PUB_RESULT_SOURCE_ORDER')
        cursor_groups = [group for group in groups if group['graphCursorSteps'] > 0]
        if graph_capture_enabled:
            populated_checkpoints = [value for value in checkpoints.values() if value is not None]
            PK_P07_need(cursor_groups and min((group['ordinal'] for group in cursor_groups)) > max((value['group'] for value in populated_checkpoints)), 'GRAPH_CURSOR_CHECKPOINT_ORDER')
            first_output_group = min((row['group'] for row, _line, _decoded in publisher_chunks))
            PK_P07_need(max((group['ordinal'] for group in cursor_groups)) < first_output_group, 'GRAPH_CURSOR_BEFORE_OUTPUT')
            PK_P07_need(graph_result is not None and graph_chunks and (graph_result_line is not None), 'GRAPH_CAPTURE_MISSING')
            graph_payload = b''.join((decoded for _row, _line, decoded in graph_chunks))
            graph_capture = PK_P07_validate_graph_document(graph_payload, graph_chunks, graph_result, graph_result_line, publisher_capture, h, result, entry, source_sha, branches, net_order, groups, pads)
        else:
            PK_P07_need(not graph_chunks and graph_result is None and all((group['graphCursorSteps'] == 0 and group['graphChunkRows'] == 0 and (group['graphResultRows'] == 0) for group in groups)), 'GRAPH_DISABLED_RECORD')
        emitted_by_group = {}
        for row, line, decoded in publisher_chunks:
            bucket = emitted_by_group.setdefault(row['group'], [0, 0])
            bucket[0] += len(decoded)
            bucket[1] += len(line)
        emitted_by_group.setdefault(publisher_result['group'], [0, 0])[1] += len(publisher_result_line)
        for row, line, decoded in graph_chunks:
            bucket = emitted_by_group.setdefault(row['group'], [0, 0])
            bucket[0] += len(decoded)
            bucket[1] += len(line)
        if graph_result is not None:
            emitted_by_group.setdefault(graph_result['group'], [0, 0])[1] += len(graph_result_line)
        for group in groups:
            decoded_bytes, line_bytes = emitted_by_group.get(group['ordinal'], (0, 0))
            PK_P07_need(group['diagnosticSerializedBytes'] == decoded_bytes + line_bytes, 'DIAGNOSTIC_OUTPUT_BYTES')
            PK_P07_need(group['diagnosticHashBytes'] >= decoded_bytes + line_bytes and group['diagnosticCopiedEntries'] >= decoded_bytes, 'DIAGNOSTIC_GROUP_CAPTURE_WORK')
        expected_serialized = publisher_capture['payloadBytes'] + sum((len(line) for _row, line, _decoded in publisher_chunks)) + len(publisher_result_line)
        if graph_capture is not None:
            expected_serialized += graph_capture['payloadBytes'] + sum((len(line) for _row, line, _decoded in graph_chunks)) + len(graph_result_line)
        PK_P07_need(sum((group['diagnosticSerializedBytes'] for group in groups)) == expected_serialized, 'DIAGNOSTIC_SERIALIZED_CENSUS')
        expected_hash_bytes = publisher_capture['captureHashBytes']
        if graph_capture is not None:
            expected_hash_bytes += graph_capture['captureHashBytes']
        PK_P07_need(sum((group['diagnosticHashBytes'] for group in groups)) == expected_hash_bytes, 'DIAGNOSTIC_HASH_CENSUS')
        path_node_count = len(publisher_capture['document']['path']['nodes'])
        path_copy_floor = path_node_count * 12
        capture_copies = sum((group['diagnosticCopiedEntries'] for group in groups if group['phase'] == 'CAPTURE'))
        PK_P07_need(capture_copies >= path_copy_floor, 'DIAGNOSTIC_PATH_COPY_CENSUS')
        if graph_capture is not None:
            PK_P07_need(capture_copies >= path_copy_floor + 32, 'GRAPH_METADATA_COPY_FLOOR')
        found_group = checkpoints['found']['group']
        terminal_group = checkpoints['publisherTerminal']['group']
        before_geometry = publisher_capture['document']['before']
        after_geometry = publisher_capture['document']['after']

        def geometry_copy_entries(snapshot):
            return sum((7 + len(trace[7]) + len(trace[8]) for trace in snapshot['traces'])) + 8 * len(snapshot['holes'])
        synchronous_copy_floors = {}
        synchronous_copy_floors[found_group] = len(publisher_capture['document']['branch']['reachedPadIds']) + 24 + geometry_copy_entries(before_geometry)
        synchronous_copy_floors[terminal_group] = synchronous_copy_floors.get(terminal_group, 0) + geometry_copy_entries(after_geometry) + PK_P07_utf16_length(PK_P07_geometry_fingerprint(after_geometry['staticPrefix'], after_geometry['traces'], after_geometry['holes']))
        PK_P07_need(all((floor <= PK_P07_LIMITS['snapshotEntries'] and groups[group]['diagnosticCopiedEntries'] >= floor for group, floor in synchronous_copy_floors.items())), 'DIAGNOSTIC_SYNCHRONOUS_SNAPSHOT')
        required_hash_bytes += publisher_capture['captureHashBytes']
        if graph_capture is not None:
            required_hash_bytes += graph_capture['captureHashBytes']
    else:
        PK_P07_need(not publisher_chunks and publisher_result is None and (not graph_chunks) and (graph_result is None) and (not graph_capture_enabled), 'PUB_NON_CAPTURE_RECORD')
    PK_P07_need(sum((g['hashInputBytes'] for g in groups)) >= required_hash_bytes, 'HASH_INPUT_CENSUS')
    lw = result['logicalWork']
    PK_P07_keys(lw, ('generatorAdvances', 'p07Slices', 'p07OwnerSteps', 'diagnosticCloneSteps', 'routingOwnerSteps', 'expansions'), 'LOGICAL_WORK_SCHEMA')
    raw_owner_steps = sum((g['p07Steps'] or 0 for g in groups))
    measured = dict(generatorAdvances=sum((g['generatorAdvances'] for g in groups)), p07Slices=sum((g['p07Slices'] for g in groups)), p07OwnerSteps=raw_owner_steps, diagnosticCloneSteps=diagnostic_clone_steps, routingOwnerSteps=raw_owner_steps - diagnostic_clone_steps, expansions=r['expansions'])
    PK_P07_need(lw == measured and all((PK_P07_integer(x) for x in lw.values())) and (lw['p07Slices'] > 0), 'LOGICAL_WORK_COUNTS')
    PK_P07_need(PK_P07_integer(entry['elapsedToCloseNanos'], 1, PK_P07_LIMITS['operationNanos']) and PK_P07_integer(entry['maxFullGroupNanos'], 1, PK_P07_LIMITS['groupNanos']) and (entry['maxFullGroupNanos'] >= max((g['elapsedBeforeOwnLineNanos'] for g in groups))) and (entry['elapsedToCloseNanos'] >= sum((g['elapsedBeforeOwnLineNanos'] for g in groups))), 'FINAL_TIMING_BOUND')
    return dict(header=h, pads=pads, branches=branches, groups=groups, diagnosticClones=[row for _, row in diagnostic_events], netOrder=net_order, netOrderAudit=net_order_audit, result=result, choices=choices, publisherCapture=publisher_capture, graphCapture=graph_capture)

def PK_stable_cohort_order(net_ids, cohort_ids):
    return PK_N_stable_cohort_order(net_ids, cohort_ids)

def PK_validate_prior_run(run, kind, captured, cohort_ids):
    return PK_N__validate_run(run, kind, captured, cohort_ids)

def PK_validate_prior_terminal_failure(value, outcome, ordering, run_work, *, control):
    return PK_N__validate_terminal_failure(value, outcome, ordering, run_work, control=control)

def PK_result_tree_minimum_units(root):
    return PK_N__result_tree_minimum_units(root)

def PK_validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind):
    return PK_P07_validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind)
CC___doc__ = "Pure codec for the authored Q100 terminal telemetry input context.\n\nThis module deliberately has no filesystem, process, environment, or dynamic\nloader behavior. It validates current context structure and emits nativeProjection\nas generated Java DATA. It does not authenticate prior artifacts or qualify the\ncontext's physical data.\n"
import hashlib as CC_hashlib
import json as CC_json
import re as CC_re
CC_CONTEXT_SCHEMA = 'tsj-cohort-terminal-input-context-v2'
CC_GENERATED_CLASS = 'CohortTerminalTelemetryInputV2'
CC_JAVA_PACKAGE = 'com.lushprojects.circuitjs1.client'
CC_MAX_CONTEXT_BYTES = 1048576
CC_MAX_GENERATED_BYTES = 1048576
CC_MAX_DATA_ENTRIES = 4096
CC_MAX_DEPTH = 32
CC_MAX_VISITS = 1000000
CC_MAX_TEXT_LENGTH = 256
CC_MAX_INT = 2 ** 31 - 1
CC_MIN_INT = -2 ** 31
CC_MAX_GRID_SIDE = 250000
CC_MAX_GRID_CELLS = 250000
CC_PAD_COUNT = 246
CC_TOP_KEYS = ('schema', 'nativeProjection', 'physicalContext')
CC_NATIVE_PROJECTION_KEYS = ('prior', 'capture', 'ownerSettings', 'seedValues', 'sourceIdentity')
CC_PRIOR_KEYS = ('sourceParent', 'manifestSha256', 'sourceManifestSha256', 'readerSha256', 'resultSha256', 'operation1Sha256', 'parentReceiptSha256', 'priorRunId', 'priorOriginalReceipt', 'trialDisposition')
CC_PRIOR_RECEIPT_KEYS = ('receiptPath', 'receiptBytes', 'receiptSha256', 'receiptOrdinal', 'lineageOrdinal')
CC_TRIAL_DISPOSITION_KEYS = ('status', 'reason', 'capturedFailedNetId', 'controlFailedNetId', 'cohortNetIds', 'requestedNetIds', 'orderChanged', 'feedbackValidated')
CC_CAPTURE_KEYS = ('cohortNetIds', 'capturedNetIds', 'trialDisposition', 'controlDisposition', 'trialOutcome', 'passIndex', 'failedNetId', 'priorPromotedNetId', 'liveSearchIndex')
CC_OWNER_KEYS = ('boardMode', 'maxExpansions', 'expandedAtFailure', 'maxVias', 'viaCost', 'maxTransitions', 'branchStrategy', 'netStrategy', 'roleWidthsEnabled', 'orderingStreamEnabled')
CC_SEED_KEYS = ('rootSeed', 'placementSeed', 'routingSeed')
CC_SOURCE_IDENTITY_KEYS = ('geometryBeforeSha256', 'geometryAfterSha256', 'detachedGeometrySha256', 'effectsBeforeSha256', 'effectsAfterSha256', 'traceCount', 'holeCount', 'padLandIdentity')
CC_PAD_IDENTITY_KEYS = ('format', 'rowCount', 'sha256')
CC_PHYSICAL_CONTEXT_KEYS = ('gridWidth', 'gridHeight', 'padLands', 'qualifiedP07Pads')
CC_PAD_LAND_KEYS = ('padId', 'netId', 'x', 'y', 'bounds', 'attachment', 'layer')
CC_P07_PAD_KEYS = ('record', 'group', 'padId', 'netId', 'componentId', 'x', 'y', 'role')
CC__HEX_SHA256 = CC_re.compile('[0-9a-f]{64}\\Z')
CC__SIGNED_LONG = CC_re.compile('(?:0|-[1-9][0-9]*|[1-9][0-9]*)\\Z')
CC__TOKEN = CC_re.compile('[-+A-Za-z0-9_.:]{1,128}\\Z')
CC__GIT_SHA1 = CC_re.compile('[0-9a-f]{40}\\Z')
CC__UUID = CC_re.compile('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z')

class CC_ContextCodecError(ValueError):
    """A stable, machine-readable input rejection."""

    def __init__(self, code):
        super().__init__(code)
        self.code = code

def CC__need(condition, code):
    if not condition:
        raise CC_ContextCodecError(code)

def CC__exact_keys(value, expected, code):
    CC__need(type(value) is dict and tuple(value.keys()) == expected, code)

def CC__ascii(value, maximum=CC_MAX_TEXT_LENGTH, allow_empty=False, code='ASCII'):
    CC__need(type(value) is str and (allow_empty or bool(value)) and (len(value) <= maximum) and all((32 <= ord(character) <= 126 for character in value)), code)
    return value

def CC__sha256(value, code):
    CC__need(type(value) is str and CC__HEX_SHA256.fullmatch(value) is not None, code)

def CC__integer(value, low, high, code):
    CC__need(type(value) is int and low <= value <= high, code)
    return value

def CC__unique_strings(value, length, code):
    CC__need(type(value) is list and len(value) == length, code + '_COUNT')
    for item in value:
        CC__ascii(item, code=code + '_ITEM')
    CC__need(len(set(value)) == length, code + '_DUPLICATE')
    return value

def CC__reject_float(_text):
    raise CC_ContextCodecError('JSON_FLOAT')

def CC__reject_constant(_text):
    raise CC_ContextCodecError('JSON_NONFINITE')

def CC__parse_int(text):
    value = int(text)
    CC__integer(value, CC_MIN_INT, CC_MAX_INT, 'JSON_INTEGER_RANGE')
    return value

def CC__pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise CC_ContextCodecError('JSON_DUPLICATE_KEY')
        result[key] = value
    return result

def CC__walk_json(value):
    """Check the bounded JSON domain and reject cycles or shared containers."""
    visited = 0
    containers = set()

    def visit(node, depth):
        nonlocal visited
        CC__need(depth <= CC_MAX_DEPTH, 'JSON_DEPTH')
        visited += 1
        CC__need(visited <= CC_MAX_VISITS, 'JSON_VISITS')
        kind = type(node)
        if node is None or kind is bool:
            return
        if kind is int:
            CC__integer(node, CC_MIN_INT, CC_MAX_INT, 'JSON_INTEGER_RANGE')
            return
        if kind is str:
            CC__ascii(node, code='JSON_STRING')
            return
        if kind is list or kind is dict:
            identity = id(node)
            CC__need(identity not in containers, 'JSON_ALIAS')
            containers.add(identity)
            if kind is list:
                for child in node:
                    visit(child, depth + 1)
                return
            for key, child in node.items():
                CC__need(type(key) is str, 'JSON_OBJECT_KEY_TYPE')
                visit(key, depth + 1)
                visit(child, depth + 1)
            return
        raise CC_ContextCodecError('JSON_VALUE_TYPE')
    visit(value, 0)
    return visited

def CC_canonical_json(value):
    """Encode an ordered JSON tree as compact printable ASCII with one LF."""
    CC__walk_json(value)
    try:
        raw = (CC_json.dumps(value, ensure_ascii=True, sort_keys=False, separators=(',', ':'), allow_nan=False) + '\n').encode('ascii')
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise CC_ContextCodecError('JSON_SERIALIZE') from None
    return raw

def CC__validate_trial_disposition(value, code):
    CC__exact_keys(value, CC_TRIAL_DISPOSITION_KEYS, code + '_KEYS')
    for key in ('status', 'reason', 'capturedFailedNetId', 'controlFailedNetId'):
        CC__ascii(value[key], code=code + '_' + key.upper())
    cohort = CC__unique_strings(value['cohortNetIds'], 2, code + '_COHORT')
    requested = CC__unique_strings(value['requestedNetIds'], 60, code + '_REQUESTED')
    CC__need(cohort == ['A_COIL_LOW', 'B_COIL_LOW'] and set(cohort).issubset(set(requested)), code + '_COHORT_BINDING')
    CC__need(type(value['orderChanged']) is bool and type(value['feedbackValidated']) is bool, code + '_FLAGS')
    CC__need(value['status'] == 'RUN' and value['reason'] == 'TRIAL_EXECUTED' and (value['capturedFailedNetId'] == 'A_COIL_LOW') and (value['controlFailedNetId'] == 'B_COIL_LOW') and (value['feedbackValidated'] is True), code + '_QUALIFIED')

def CC__validate_prior(value):
    CC__exact_keys(value, CC_PRIOR_KEYS, 'PRIOR_KEYS')
    CC__need(type(value['sourceParent']) is str and CC__GIT_SHA1.fullmatch(value['sourceParent']) is not None, 'PRIOR_SOURCE_PARENT')
    for key in ('manifestSha256', 'sourceManifestSha256', 'readerSha256', 'resultSha256', 'operation1Sha256', 'parentReceiptSha256'):
        CC__sha256(value[key], 'PRIOR_' + key.upper())
    CC__need(type(value['priorRunId']) is str and CC__UUID.fullmatch(value['priorRunId']) is not None, 'PRIOR_RUN_ID')
    receipt = value['priorOriginalReceipt']
    CC__exact_keys(receipt, CC_PRIOR_RECEIPT_KEYS, 'PRIOR_RECEIPT_KEYS')
    CC__need(receipt['receiptPath'] == 'op-1.jsonl', 'PRIOR_RECEIPT_PATH')
    CC__integer(receipt['receiptBytes'], 1, CC_MAX_INT, 'PRIOR_RECEIPT_BYTES')
    CC__sha256(receipt['receiptSha256'], 'PRIOR_RECEIPT_SHA256')
    CC__need(receipt['receiptSha256'] == value['operation1Sha256'], 'PRIOR_RECEIPT_OPERATION_BINDING')
    CC__need(type(receipt['receiptOrdinal']) is int and receipt['receiptOrdinal'] == 307 and (type(receipt['lineageOrdinal']) is int) and (receipt['lineageOrdinal'] == 77), 'PRIOR_RECEIPT_ORDINALS')
    CC__validate_trial_disposition(value['trialDisposition'], 'PRIOR_TRIAL')

def CC__validate_capture(value):
    CC__exact_keys(value, CC_CAPTURE_KEYS, 'CAPTURE_KEYS')
    cohort = CC__unique_strings(value['cohortNetIds'], 2, 'CAPTURE_COHORT')
    captured = CC__unique_strings(value['capturedNetIds'], 60, 'CAPTURE_NETS')
    CC__need(cohort == ['A_COIL_LOW', 'B_COIL_LOW'] and set(cohort).issubset(set(captured)), 'CAPTURE_COHORT_BINDING')
    trial = value['trialDisposition']
    CC__validate_trial_disposition(trial, 'CAPTURE_TRIAL')
    CC__need(trial['cohortNetIds'] == cohort, 'CAPTURE_TRIAL_COHORT')
    CC__need(value['controlDisposition'] == 'NO_PATH' and value['trialOutcome'] == 'CAP_REJECTED', 'CAPTURE_OUTCOMES')
    CC__integer(value['passIndex'], 2, 2, 'CAPTURE_PASS')
    CC__need(value['failedNetId'] == 'A_COIL_LOW' and value['priorPromotedNetId'] == 'D_SENSE', 'CAPTURE_NET_BINDING')
    CC__need(captured[43] == value['failedNetId'] and value['priorPromotedNetId'] in captured, 'CAPTURE_VECTOR_BINDING')
    CC__integer(value['liveSearchIndex'], 44, 44, 'CAPTURE_LIVE_SEARCH_INDEX')
    for key in ('controlDisposition', 'trialOutcome', 'failedNetId', 'priorPromotedNetId'):
        CC__ascii(value[key], code='CAPTURE_' + key.upper())

def CC__validate_owner_settings(value):
    CC__exact_keys(value, CC_OWNER_KEYS, 'OWNER_SETTINGS_KEYS')
    CC__need(value['boardMode'] == 'FULLER_TWO_LAYER' and value['maxExpansions'] == 1000000 and (value['expandedAtFailure'] == 918222) and (value['maxVias'] == 48) and (value['viaCost'] == 60) and (value['maxTransitions'] == 4) and (value['branchStrategy'] == 'CANONICAL_NEAREST') and (value['netStrategy'] == 'CANONICAL') and (value['roleWidthsEnabled'] is True) and (value['orderingStreamEnabled'] is True), 'OWNER_SETTINGS_FIXED')
    for key, low, high in (('maxExpansions', 1, 1000000), ('expandedAtFailure', 0, 1000000), ('maxVias', 0, 48), ('viaCost', 0, 1000000), ('maxTransitions', 0, 4)):
        CC__integer(value[key], low, high, 'OWNER_' + key.upper())
    for key in ('boardMode', 'branchStrategy', 'netStrategy'):
        CC__ascii(value[key], code='OWNER_' + key.upper())
    for key in ('roleWidthsEnabled', 'orderingStreamEnabled'):
        CC__need(type(value[key]) is bool, 'OWNER_' + key.upper() + '_TYPE')

def CC__validate_seed_values(value):
    CC__exact_keys(value, CC_SEED_KEYS, 'SEED_KEYS')
    expected = {'rootSeed': '9007199254740993', 'placementSeed': '-6587958367974952701', 'routingSeed': '8445301674744508279'}
    for key in CC_SEED_KEYS:
        seed = CC__ascii(value[key], maximum=20, code='SEED_' + key.upper())
        CC__need(CC__SIGNED_LONG.fullmatch(seed) is not None, 'SEED_' + key.upper() + '_FORMAT')
        integer = int(seed)
        CC__need(-2 ** 63 <= integer <= 2 ** 63 - 1, 'SEED_' + key.upper() + '_RANGE')
    CC__need(value == expected, 'SEED_VALUES_FIXED')

def CC__validate_source_identity(value):
    CC__exact_keys(value, CC_SOURCE_IDENTITY_KEYS, 'SOURCE_IDENTITY_KEYS')
    for key in ('geometryBeforeSha256', 'geometryAfterSha256', 'detachedGeometrySha256', 'effectsBeforeSha256', 'effectsAfterSha256'):
        CC__sha256(value[key], 'SOURCE_IDENTITY_' + key.upper())
    CC__integer(value['traceCount'], 0, 0, 'SOURCE_IDENTITY_TRACE_COUNT')
    CC__integer(value['holeCount'], 0, 0, 'SOURCE_IDENTITY_HOLE_COUNT')
    pad = value['padLandIdentity']
    CC__exact_keys(pad, CC_PAD_IDENTITY_KEYS, 'PAD_IDENTITY_KEYS')
    CC__need(pad['format'] == 'TSJ_PAD_LAND_ASCII_V1', 'PAD_IDENTITY_FORMAT')
    CC__integer(pad['rowCount'], CC_PAD_COUNT, CC_PAD_COUNT, 'PAD_IDENTITY_COUNT')
    CC__sha256(pad['sha256'], 'PAD_IDENTITY_SHA256')
    CC__need(pad['sha256'] == '541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0', 'PAD_IDENTITY_FIXED')

def CC__validate_pad_land(row, code):
    CC__exact_keys(row, CC_PAD_LAND_KEYS, code + '_KEYS')
    pad_id = row['padId']
    CC__need(type(pad_id) is str and CC__TOKEN.fullmatch(pad_id) is not None, code + '_ID')
    CC__need(type(row['netId']) is str and CC__TOKEN.fullmatch(row['netId']) is not None, code + '_NET')
    CC__integer(row['x'], -1000000, 1000000, code + '_X')
    CC__integer(row['y'], -1000000, 1000000, code + '_Y')
    bounds = row['bounds']
    CC__need(type(bounds) is list and len(bounds) == 4, code + '_BOUNDS_SHAPE')
    for item in bounds:
        CC__integer(item, -100000000, 100000000, code + '_BOUNDS_VALUE')
    x, y, width, height = bounds
    CC__need(width > 0 and height > 0 and (-100000000 <= x + width <= 100000000) and (-100000000 <= y + height <= 100000000) and (x <= row['x'] <= x + width) and (y <= row['y'] <= y + height), code + '_BOUNDS_BINDING')
    CC__need(row['attachment'] == 'PLATED_THROUGH_HOLE' and row['layer'] == 'TOP+BOTTOM', code + '_PHYSICAL_TYPE')
    return pad_id

def CC__validate_p07_pad(row, code):
    CC__exact_keys(row, CC_P07_PAD_KEYS, code + '_KEYS')
    CC__need(row['record'] == 'PAD', code + '_RECORD')
    CC__integer(row['group'], 0, 639, code + '_GROUP')
    pad_id = row['padId']
    for key in ('padId', 'netId', 'componentId', 'role'):
        CC__need(type(row[key]) is str and CC__TOKEN.fullmatch(row[key]) is not None, code + '_' + key.upper())
    CC__integer(row['x'], -1000000, 1000000, code + '_X')
    CC__integer(row['y'], -1000000, 1000000, code + '_Y')
    return pad_id

def CC__validate_physical_context(value):
    CC__exact_keys(value, CC_PHYSICAL_CONTEXT_KEYS, 'PHYSICAL_CONTEXT_KEYS')
    width = CC__integer(value['gridWidth'], 1, CC_MAX_GRID_SIDE, 'GRID_WIDTH')
    height = CC__integer(value['gridHeight'], 1, CC_MAX_GRID_SIDE, 'GRID_HEIGHT')
    CC__need(width * height <= CC_MAX_GRID_CELLS, 'GRID_AREA')
    pad_lands = value['padLands']
    p07_rows = value['qualifiedP07Pads']
    CC__need(type(pad_lands) is list and len(pad_lands) == CC_PAD_COUNT, 'PAD_LAND_COUNT')
    CC__need(type(p07_rows) is list and len(p07_rows) == CC_PAD_COUNT, 'P07_PAD_COUNT')
    land_by_id = {}
    previous_id = None
    for row in pad_lands:
        pad_id = CC__validate_pad_land(row, 'PAD_LAND')
        CC__need(previous_id is None or previous_id < pad_id, 'PAD_LAND_ORDER')
        previous_id = pad_id
        land_by_id[pad_id] = row
    p07_by_id = {}
    role_by_net = {}
    previous_group = None
    for row in p07_rows:
        pad_id = CC__validate_p07_pad(row, 'P07_PAD')
        group = row['group']
        CC__need(previous_group is None or previous_group <= group, 'P07_PAD_GROUP_ORDER')
        previous_group = group
        CC__need(pad_id not in p07_by_id, 'P07_PAD_DUPLICATE')
        p07_by_id[pad_id] = row
        net_id = row['netId']
        prior_role = role_by_net.setdefault(net_id, row['role'])
        CC__need(prior_role == row['role'], 'P07_NET_ROLE_INCONSISTENT')
    CC__need(set(land_by_id) == set(p07_by_id), 'PAD_CENSUS_BINDING')
    for pad_id, land in land_by_id.items():
        row = p07_by_id[pad_id]
        CC__need(land['netId'] == row['netId'] and land['x'] == row['x'] and (land['y'] == row['y']), 'PAD_P07_PHYSICAL_BINDING')

def CC_validate_context(value):
    """Validate the exact current three-object canonical context schema."""
    CC__exact_keys(value, CC_TOP_KEYS, 'CONTEXT_KEYS')
    CC__walk_json(value)
    CC__need(value['schema'] == CC_CONTEXT_SCHEMA, 'CONTEXT_SCHEMA')
    projection = value['nativeProjection']
    CC__exact_keys(projection, CC_NATIVE_PROJECTION_KEYS, 'NATIVE_PROJECTION_KEYS')
    CC__validate_prior(projection['prior'])
    CC__validate_capture(projection['capture'])
    CC__need(projection['prior']['trialDisposition'] == projection['capture']['trialDisposition'], 'PRIOR_CAPTURE_TRIAL_BINDING')
    CC__validate_owner_settings(projection['ownerSettings'])
    CC__validate_seed_values(projection['seedValues'])
    CC__validate_source_identity(projection['sourceIdentity'])
    CC__validate_physical_context(value['physicalContext'])
    return value

def CC_canonical_context(value):
    """Return validated canonical context bytes, including exactly one LF."""
    CC_validate_context(value)
    raw = CC_canonical_json(value)
    CC__need(0 < len(raw) <= CC_MAX_CONTEXT_BYTES, 'CONTEXT_SIZE')
    return raw

def CC_parse_context(raw):
    """Parse one strict canonical ASCII context line without duplicate keys."""
    CC__need(type(raw) is bytes and 0 < len(raw) <= CC_MAX_CONTEXT_BYTES, 'CONTEXT_DOCUMENT_SIZE')
    CC__need(raw.endswith(b'\n') and (not raw.endswith(b'\n\n')) and (b'\r' not in raw) and (not raw.startswith(b'\xef\xbb\xbf')), 'CONTEXT_FRAMING')
    try:
        text = raw[:-1].decode('ascii', errors='strict')
        value = CC_json.loads(text, object_pairs_hook=CC__pairs_no_duplicates, parse_float=CC__reject_float, parse_constant=CC__reject_constant, parse_int=CC__parse_int)
    except CC_ContextCodecError:
        raise
    except (UnicodeDecodeError, CC_json.JSONDecodeError, ValueError, RecursionError):
        raise CC_ContextCodecError('CONTEXT_JSON') from None
    CC_validate_context(value)
    CC__need(raw == CC_canonical_context(value), 'CONTEXT_NOT_CANONICAL')
    return value

def CC_context_sha256(value):
    """Return the SHA-256 of the canonical current context."""
    return CC_hashlib.sha256(CC_canonical_context(value)).hexdigest()

def CC__clone_json(value):
    kind = type(value)
    if kind is dict:
        return {key: CC__clone_json(child) for key, child in value.items()}
    if kind is list:
        return [CC__clone_json(child) for child in value]
    return value

def CC_native_projection(value):
    """Return a detached fresh JSON tree for the native factory DATA view."""
    CC_validate_context(value)
    return CC__clone_json(value['nativeProjection'])

def CC_data_entries(value):
    """Count JSON container entries; this is inventory, not total factory work."""
    CC__need(type(value) in (dict, list), 'DATA_ROOT_TYPE')
    CC__walk_json(value)

    def count(node):
        total = 1 + len(node)
        CC__need(total <= CC_MAX_DATA_ENTRIES, 'DATA_ENTRIES_LIMIT')
        children = node.values() if type(node) is dict else node
        for child in children:
            if type(child) in (dict, list):
                total += count(child)
                CC__need(total <= CC_MAX_DATA_ENTRIES, 'DATA_ENTRIES_LIMIT')
        return total
    return count(value)

def CC_java_ascii_literal(value):
    """Port of the pinned ASCII-only, fixed-width octal Java literal encoder."""
    CC__ascii(value, maximum=CC_MAX_TEXT_LENGTH, allow_empty=True, code='JAVA_LITERAL_TEXT')
    return '"' + ''.join(('\\%03o' % ord(character) for character in value)) + '"'

def CC_generate_input_java(value, expected_context_sha256=None):
    """Emit deterministic Java DATA with the frozen V2 factory API."""
    raw_context = CC_canonical_context(value)
    digest = CC_hashlib.sha256(raw_context).hexdigest()
    if expected_context_sha256 is not None:
        CC__sha256(expected_context_sha256, 'EXPECTED_CONTEXT_SHA256')
        CC__need(digest == expected_context_sha256, 'EXPECTED_CONTEXT_SHA256_MISMATCH')
    tree = CC__clone_json(value['nativeProjection'])
    entries = CC_data_entries(tree)
    lines = ['package ' + CC_JAVA_PACKAGE + ';', '', 'public final class ' + CC_GENERATED_CLASS + ' {', '    private ' + CC_GENERATED_CLASS + '() {}', '    public static String schemaTag() { return ' + CC_java_ascii_literal(CC_CONTEXT_SCHEMA) + '; }', '    public static String contextSha256() { return ' + CC_java_ascii_literal(digest) + '; }', '    public static int dataEntries() { return ' + str(entries) + '; }', '    public static java.util.Map<String,Object> dataJson() {']
    serial = [0]

    def emit(node, indent):
        kind = type(node)
        if kind is dict:
            name = 'm' + str(serial[0])
            serial[0] += 1
            lines.append(indent + 'java.util.Map<String,Object> ' + name + ' = new java.util.LinkedHashMap<String,Object>();')
            for key, child in node.items():
                CC__need(type(key) is str, 'JSON_OBJECT_KEY_TYPE')
                expression = emit(child, indent)
                lines.append(indent + name + '.put(' + CC_java_ascii_literal(key) + ', ' + expression + ');')
            return name
        if kind is list:
            name = 'l' + str(serial[0])
            serial[0] += 1
            lines.append(indent + 'java.util.List<Object> ' + name + ' = new java.util.ArrayList<Object>();')
            for child in node:
                expression = emit(child, indent)
                lines.append(indent + name + '.add(' + expression + ');')
            return name
        if kind is str:
            return CC_java_ascii_literal(node)
        if kind is bool:
            return 'true' if node else 'false'
        if node is None:
            return 'null'
        CC__integer(node, CC_MIN_INT, CC_MAX_INT, 'GENERATED_INTEGER')
        return 'Integer.valueOf(' + str(node) + ')'
    root_name = emit(tree, '        ')
    lines.append('        return ' + root_name + ';')
    lines.append('    }')
    lines.append('}')
    try:
        raw = ('\n'.join(lines) + '\n').encode('ascii')
    except UnicodeEncodeError:
        raise CC_ContextCodecError('GENERATED_SOURCE_ASCII') from None
    CC__need(len(raw) <= CC_MAX_GENERATED_BYTES, 'GENERATED_SOURCE_SIZE')
    CC__need(b'java.util.Map<String,Object>' in raw and b'java.util.LinkedHashMap<String,Object>' in raw and (b'dataJson()' in raw), 'GENERATED_SOURCE_CONTENT')
    return raw
OV2___doc__ = 'Independent, profile-neutral observation V2 validation.\n\nThis source is embedded verbatim in standalone consumers. It owns no I/O,\nproducer imports, retained input references, or mutable accounting state.\n'
import types as OV2__ov2_types
OV2__ov2_MAX_INT = 2147483647
OV2__ov2_MIN_INT = -2147483648
OV2__ov2_OBSERVATION_KEYS = ('schema', 'mode', 'state', 'callbackCount', 'foundCallbackCount', 'nonFoundCallbackCount', 'firstNetOrdinal', 'lastNetOrdinal', 'lastCallbackTotalExpanded', 'selectedCallbackOrdinal', 'transientTupleCopies', 'transientFieldCopies', 'validationCount', 'observerUnits', 'copiedEntries', 'copyChunks', 'selectedSnapshot', 'selectedHead')
OV2__ov2_SNAPSHOT_KEYS = ('pass', 'netId', 'startPadId', 'rootPadId', 'startX', 'startY', 'rootX', 'rootY', 'gridWidth', 'gridHeight', 'goalsTop', 'goalsBottom', 'prefixViaCount', 'reachedPadCount', 'initialManhattanDistance', 'minimumExploredManhattanDistance', 'branchExpanded', 'totalExpanded', 'expandedTop', 'expandedBottom', 'frontierCount', 'bestStateCount', 'terminalReason', 'relaxedBoundStarted', 'relaxedBoundComplete', 'relaxedBoundObservationChecked', 'relaxedBoundLiveLayoutStable', 'relaxedBoundRouterStateStable', 'relaxedBoundRouterEffectsStable', 'relaxedBoundWorkUnits', 'relaxedBoundOrdinaryProbes', 'relaxedBoundViaProbes', 'relaxedBoundRefreshes')
OV2__ov2_HEAD_KEYS = ('branchOrdinal', 'cell', 'layer', 'direction', 'transitions', 'cost', 'storedHeuristic', 'serial', 'key', 'storedPriority')

class OV2_ObservationV2Error(Exception):

    def __init__(self, code):
        self.code = code
        super().__init__(code)

def OV2__ov2_need(condition, code):
    if not condition:
        raise OV2_ObservationV2Error(code)

def OV2__ov2_int(value, low=0, high=OV2__ov2_MAX_INT):
    return type(value) is int and low <= value <= high

def OV2__ov2_keys(value, expected, code):
    OV2__ov2_need(type(value) is dict and tuple(value) == expected, code)

def OV2__ov2_context(requested, traversed, pads_by_id, pads_by_net, grid):
    OV2__ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple) and all((type(net) is str and bool(net) for net in requested)) and all((type(net) is str and bool(net) for net in traversed)), 'OBSERVATION_CONTEXT_ORDERING')
    OV2__ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict, 'OBSERVATION_CONTEXT_PADS')
    OV2__ov2_need(all((type(pad) is str and bool(pad) and (type(row) is tuple) and (len(row) == 3) and (type(row[0]) is str) and bool(row[0]) and OV2__ov2_int(row[1], OV2__ov2_MIN_INT) and OV2__ov2_int(row[2], OV2__ov2_MIN_INT) for pad, row in pads_by_id.items())), 'OBSERVATION_CONTEXT_PAD_ROWS')
    OV2__ov2_need(all((type(net) is str and bool(net) and (type(members) is set) and all((type(pad) is str and bool(pad) for pad in members)) for net, members in pads_by_net.items())), 'OBSERVATION_CONTEXT_MEMBERSHIP')
    OV2__ov2_need(type(grid) is tuple and len(grid) == 2 and OV2__ov2_int(grid[0], 1, 250000) and OV2__ov2_int(grid[1], 1, 250000) and (grid[0] * grid[1] <= 250000), 'PRIOR_GRID_CONTEXT')

class OV2_ObservationV2Kernel:

    @staticmethod
    def validate_snapshot(s, requested, traversed, pads_by_id, pads_by_net, grid):
        OV2__ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        OV2__ov2_keys(s, OV2__ov2_SNAPSHOT_KEYS, 'SNAPSHOT_SCHEMA')
        net = s['netId']
        OV2__ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple) and (type(net) is str) and (net in requested), 'SNAPSHOT_NET')
        ordinal = requested.index(net) + 1
        OV2__ov2_need(ordinal <= len(traversed) and traversed[ordinal - 1] == net, 'SNAPSHOT_TRAVERSAL_BINDING')
        start, root = (s['startPadId'], s['rootPadId'])
        OV2__ov2_need(type(start) is str and bool(start) and (type(root) is str) and bool(root), 'SNAPSHOT_PAD_IDENTITY')
        OV2__ov2_need(all((OV2__ov2_int(s[name], OV2__ov2_MIN_INT) for name in ('startX', 'startY', 'rootX', 'rootY'))), 'SNAPSHOT_PAD_COORDINATES')
        OV2__ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict and (start in pads_by_id) and (root in pads_by_id) and (start in pads_by_net.get(net, ())) and (root in pads_by_net.get(net, ())), 'SNAPSHOT_PAD_IDENTITY')
        start_row, root_row = (pads_by_id[start], pads_by_id[root])
        OV2__ov2_need(type(start_row) is tuple and len(start_row) == 3 and (type(root_row) is tuple) and (len(root_row) == 3), 'SNAPSHOT_PAD_IDENTITY')
        OV2__ov2_need(start_row[0] == root_row[0] == net and all((OV2__ov2_int(coordinate, OV2__ov2_MIN_INT) for coordinate in (start_row[1], start_row[2], root_row[1], root_row[2]))) and (s['startX'] == start_row[1]) and (s['startY'] == start_row[2]) and (s['rootX'] == root_row[1]) and (s['rootY'] == root_row[2]), 'SNAPSHOT_PAD_COORDINATES')
        OV2__ov2_need(OV2__ov2_int(s['pass'], 2, 2), 'SNAPSHOT_PASS')
        OV2__ov2_need(OV2__ov2_int(s['gridWidth'], 1, 250000) and OV2__ov2_int(s['gridHeight'], 1, 250000) and (s['gridWidth'] * s['gridHeight'] <= 250000), 'SNAPSHOT_GRID')
        OV2__ov2_need(type(grid) is tuple and len(grid) == 2 and OV2__ov2_int(grid[0], 1, 250000) and OV2__ov2_int(grid[1], 1, 250000) and (grid[0] * grid[1] <= 250000), 'PRIOR_GRID_CONTEXT')
        OV2__ov2_need((s['gridWidth'], s['gridHeight']) == grid, 'SNAPSHOT_GRID_BINDING')
        cells = s['gridWidth'] * s['gridHeight']
        OV2__ov2_need(OV2__ov2_int(s['goalsTop'], 0, cells) and OV2__ov2_int(s['goalsBottom'], 0, cells) and (s['goalsTop'] + s['goalsBottom'] > 0), 'SNAPSHOT_GOALS')
        OV2__ov2_need(OV2__ov2_int(s['prefixViaCount'], 0, 48) and OV2__ov2_int(s['reachedPadCount'], 1, len(pads_by_net.get(net, ()))) and OV2__ov2_int(s['initialManhattanDistance']), 'SNAPSHOT_COUNTS')
        branch, total = (s['branchExpanded'], s['totalExpanded'])
        OV2__ov2_need(OV2__ov2_int(branch, 0, 100000) and OV2__ov2_int(total, branch, 1000000) and OV2__ov2_int(s['expandedTop'], 0, branch) and OV2__ov2_int(s['expandedBottom'], 0, branch) and (s['expandedTop'] + s['expandedBottom'] == branch), 'SNAPSHOT_EXPANSIONS')
        minimum = s['minimumExploredManhattanDistance']
        OV2__ov2_need(OV2__ov2_int(minimum, -1, -1) if branch == 0 else OV2__ov2_int(minimum, 0, s['initialManhattanDistance']), 'SNAPSHOT_MINIMUM_DISTANCE')
        OV2__ov2_need(OV2__ov2_int(s['frontierCount']) and OV2__ov2_int(s['bestStateCount']), 'SNAPSHOT_STATE_COUNTS')
        reason = s['terminalReason']
        OV2__ov2_need(type(reason) is str and reason in ('NO_PATH', 'BRANCH_SEARCH_LIMIT', 'TOTAL_SEARCH_LIMIT', 'GRID_LIMIT', 'FOUND'), 'SNAPSHOT_TERMINAL_REASON')
        if reason == 'NO_PATH':
            OV2__ov2_need(s['frontierCount'] == 0, 'SNAPSHOT_NO_PATH_FRONTIER')
        elif reason == 'BRANCH_SEARCH_LIMIT':
            OV2__ov2_need(branch == 100000, 'SNAPSHOT_BRANCH_LIMIT')
        elif reason == 'TOTAL_SEARCH_LIMIT':
            OV2__ov2_need(total == 1000000, 'SNAPSHOT_TOTAL_LIMIT')
        elif reason == 'FOUND':
            OV2__ov2_need(branch > 0, 'SNAPSHOT_FOUND_EXPANSION')
        flags, work = (OV2__ov2_SNAPSHOT_KEYS[23:29], OV2__ov2_SNAPSHOT_KEYS[29:33])
        OV2__ov2_need(all((type(s[name]) is bool for name in flags)), 'SNAPSHOT_RELAXED_FLAGS')
        OV2__ov2_need(all((OV2__ov2_int(s[name]) for name in work)), 'SNAPSHOT_RELAXED_WORK')
        started, complete, checked = (s[name] for name in flags[:3])
        expected_checked = complete and s['prefixViaCount'] == 48
        OV2__ov2_need((not started or complete) and checked is expected_checked and all((s[name] is checked for name in flags[3:])), 'SNAPSHOT_RELAXED_STATE')
        if not started:
            OV2__ov2_need(not complete and (not checked) and all((s[name] == 0 for name in work)), 'SNAPSHOT_RELAXED_ZERO_WORK')
        return ordinal

    @staticmethod
    def validate_head(h, s):
        OV2__ov2_keys(h, OV2__ov2_HEAD_KEYS, 'HEAD_KEYS')
        OV2__ov2_need(all((OV2__ov2_int(h[name], OV2__ov2_MIN_INT) for name in OV2__ov2_HEAD_KEYS)), 'HEAD_INTS')
        OV2__ov2_keys(s, OV2__ov2_SNAPSHOT_KEYS, 'SNAPSHOT_SCHEMA')
        OV2__ov2_need(OV2__ov2_int(s['gridWidth'], 1, 250000) and OV2__ov2_int(s['gridHeight'], 1, 250000) and (s['gridWidth'] * s['gridHeight'] <= 250000) and OV2__ov2_int(s['prefixViaCount'], 0, 48), 'HEAD_SNAPSHOT')
        cells = s['gridWidth'] * s['gridHeight']
        OV2__ov2_need(h['branchOrdinal'] >= 1 and 0 <= h['cell'] < cells and (0 <= h['layer'] <= 1) and (0 <= h['direction'] <= 4) and (0 <= h['transitions'] <= 48 - s['prefixViaCount']) and (h['cost'] >= 0) and (h['storedHeuristic'] >= 0) and (h['serial'] >= 0), 'HEAD_DOMAIN')
        expected_key = ((h['transitions'] * 2 + h['layer']) * 5 + h['direction']) * cells + h['cell']
        priority = (h['cost'] + h['storedHeuristic'] + 2147483648) % 4294967296 - 2147483648
        OV2__ov2_need(h['key'] == expected_key and h['storedPriority'] == priority, 'HEAD_EQUATIONS')
        OV2__ov2_need(OV2__ov2_int(s['branchExpanded'], 100000, 100000) and OV2__ov2_int(s['totalExpanded'], 100000, 999999) and OV2__ov2_int(s['bestStateCount'], 1), 'HEAD_SNAPSHOT')

    @staticmethod
    def accounting(o):
        OV2__ov2_keys(o, OV2__ov2_OBSERVATION_KEYS, 'OBSERVATION_SCHEMA')
        d = o['callbackCount']
        OV2__ov2_need(OV2__ov2_int(d, 0, 247), 'CALLBACK_BOUND')
        h = int(o['selectedHead'] is not None)
        OV2__ov2_need(h <= d, 'HEAD_CALLBACK_BOUND')
        v = o['validationCount']
        OV2__ov2_need(OV2__ov2_int(v, d, 94 * d + 12 * h), 'VALIDATION_BOUND')
        entries, chunks, units = (33 * d + 10 * h, d + h, 69 * d + v + 21 * h)
        OV2__ov2_need(OV2__ov2_int(o['transientTupleCopies'], chunks, chunks) and OV2__ov2_int(o['copyChunks'], chunks, chunks) and OV2__ov2_int(o['transientFieldCopies'], entries, entries) and OV2__ov2_int(o['copiedEntries'], entries, entries) and OV2__ov2_int(o['observerUnits'], units, units) and (units <= 163 * d + 33 * h), 'OBSERVER_ACCOUNTING')
        return (entries, chunks, units)

    @staticmethod
    def validate_observation(o, role, opportunities, requested, traversed, pads_by_id, pads_by_net, grid, outcome, terminal):
        OV2__ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        OV2__ov2_keys(o, OV2__ov2_OBSERVATION_KEYS, 'OBSERVATION_SCHEMA')
        OV2__ov2_need(o['schema'] == 'tsj-cohort-terminal-observation-v2', 'OBSERVATION_IDENTITY')
        OV2__ov2_need(OV2__ov2_int(opportunities), 'CALLBACK_OPPORTUNITIES')
        if role == 'NULL':
            OV2__ov2_need(o['mode'] == 'NULL' and o['state'] == 'NOT_ENABLED' and all((OV2__ov2_int(o[name], 0, 0) for name in ('callbackCount', 'foundCallbackCount', 'nonFoundCallbackCount', 'transientTupleCopies', 'transientFieldCopies', 'validationCount', 'observerUnits', 'copiedEntries', 'copyChunks'))) and all((o[name] is None for name in ('firstNetOrdinal', 'lastNetOrdinal', 'lastCallbackTotalExpanded', 'selectedCallbackOrdinal', 'selectedSnapshot', 'selectedHead'))), 'NULL_OBSERVATION')
            return OV2__ov2_types.MappingProxyType({'observerUnits': 0, 'copiedEntries': 0, 'copyChunks': 0, 'state': 'NOT_ENABLED'})
        OV2__ov2_need(role == 'OBSERVE' and o['mode'] == 'OBSERVE' and (o['state'] in ('NO_FAILURE', 'FIRST_NON_FOUND', 'NON_BRANCH_REJECTION')), 'OBSERVATION_STATE')
        d, found, nonfound = (o[name] for name in ('callbackCount', 'foundCallbackCount', 'nonFoundCallbackCount'))
        OV2__ov2_need(OV2__ov2_int(d, 0, min(247, opportunities)) and OV2__ov2_int(found, 0, d) and OV2__ov2_int(nonfound, 0, d) and (found + nonfound == d), 'OBSERVATION_ACCOUNTING')
        entries, chunks, units = OV2_ObservationV2Kernel.accounting(o)
        OV2__ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple), 'OBSERVATION_ORDINALS')
        OV2__ov2_need(type(outcome) is dict and type(outcome.get('disposition')) is str and (type(outcome.get('reason')) is str) and OV2__ov2_int(outcome.get('expanded'), 0, 1000000), 'OBSERVATION_OUTCOME')
        first, last, last_total = (o[name] for name in ('firstNetOrdinal', 'lastNetOrdinal', 'lastCallbackTotalExpanded'))
        if d == 0:
            OV2__ov2_need(first is None and last is None and (last_total is None), 'OBSERVATION_EMPTY_ORDINALS')
        else:
            OV2__ov2_need(OV2__ov2_int(first, 1, len(requested)) and OV2__ov2_int(last, first, len(requested)) and (last <= len(traversed)) and OV2__ov2_int(last_total, 0, outcome['expanded']), 'OBSERVATION_ORDINALS')
        selected, selected_ordinal, head = (o[name] for name in ('selectedSnapshot', 'selectedCallbackOrdinal', 'selectedHead'))
        disposition, reason, state = (outcome['disposition'], outcome['reason'], o['state'])
        if state == 'NO_FAILURE':
            OV2__ov2_need(disposition == 'FOUND' and nonfound == 0 and (found == d) and (selected is None) and (selected_ordinal is None) and (terminal is None), 'OBSERVATION_SUCCESS_STATE')
        elif state == 'FIRST_NON_FOUND':
            OV2__ov2_need(disposition in ('NO_PATH', 'CAP_REJECTED', 'QUALITY_REJECTED') and d >= 1 and (nonfound == 1) and (found + 1 == d) and OV2__ov2_int(selected_ordinal, d, d) and (type(selected) is dict) and (type(terminal) is dict), 'OBSERVATION_FAILURE_STATE')
            net_ordinal = OV2_ObservationV2Kernel.validate_snapshot(selected, requested, traversed, pads_by_id, pads_by_net, grid)
            OV2__ov2_need(net_ordinal == last and selected['totalExpanded'] == last_total == outcome['expanded'] and (selected['netId'] == terminal.get('failedNetId')) and OV2__ov2_int(terminal.get('searchPass'), 2, 2) and (selected['terminalReason'] != 'FOUND') and (selected['terminalReason'] == reason.rsplit(':', 1)[-1]) and (reason == selected['netId'] + ':' + selected['terminalReason']) and (terminal.get('ownerReason') == reason), 'OBSERVATION_FAILURE_BINDING')
        else:
            OV2__ov2_need(disposition == 'QUALITY_REJECTED' and nonfound == 0 and (found == d) and (selected is None) and (selected_ordinal is None) and (reason == 'QUALITY_LIMIT' or reason.endswith(':BRANCH_QUALITY_LIMIT')), 'OBSERVATION_QUALITY_STATE')
            if reason == 'QUALITY_LIMIT':
                OV2__ov2_need(terminal is None, 'OBSERVATION_QUALITY_TERMINAL')
            else:
                failed = reason.split(':', 1)[0]
                OV2__ov2_need(d > 0 and type(terminal) is dict and (terminal.get('failedNetId') == failed) and (terminal.get('ownerReason') == reason) and (requested[last - 1] == failed) and (last == len(traversed)) and (last_total == outcome['expanded']), 'OBSERVATION_SCOPED_QUALITY_BINDING')
        has_branch_head = state == 'FIRST_NON_FOUND' and selected['terminalReason'] == 'BRANCH_SEARCH_LIMIT'
        OV2__ov2_need((head is not None) is has_branch_head, 'OBSERVATION_HEAD_PRESENCE')
        if has_branch_head:
            OV2_ObservationV2Kernel.validate_head(head, selected)
        return OV2__ov2_types.MappingProxyType({'observerUnits': units, 'copiedEntries': entries, 'copyChunks': chunks, 'state': state})
import hashlib
import json
import re
from types import MappingProxyType
_CONTEXT_SCHEMA = CC_CONTEXT_SCHEMA
_NATIVE_PROJECTION_KEYS = CC_NATIVE_PROJECTION_KEYS
_OWNER_KEYS = CC_OWNER_KEYS
_CONTEXT_PAD_IDENTITY_KEYS = CC_PAD_IDENTITY_KEYS
_PAD_LAND_KEYS = CC_PAD_LAND_KEYS
_PHYSICAL_CONTEXT_KEYS = CC_PHYSICAL_CONTEXT_KEYS
_CONTEXT_PRIOR_KEYS = CC_PRIOR_KEYS
_CONTEXT_PRIOR_RECEIPT_KEYS = CC_PRIOR_RECEIPT_KEYS
_P07_PAD_KEYS = CC_P07_PAD_KEYS
_SEED_KEYS = CC_SEED_KEYS
_SOURCE_IDENTITY_KEYS = CC_SOURCE_IDENTITY_KEYS
_CONTEXT_TOP_KEYS = CC_TOP_KEYS
_TRIAL_DISPOSITION_KEYS = CC_TRIAL_DISPOSITION_KEYS
_ContextCodecError = CC_ContextCodecError
_canonical_context = CC_canonical_context
_context_sha256 = CC_context_sha256
_validate_context = CC_validate_context
_ObservationV2Error = OV2_ObservationV2Error
_ObservationV2Kernel = OV2_ObservationV2Kernel
_PriorReaderError = PK_N_ReaderError
_result_tree_minimum_units = PK_result_tree_minimum_units
_stable_cohort_order = PK_stable_cohort_order
_validate_operation = PK_validate_operation
_validate_prior_run = PK_validate_prior_run
PRIOR_KERNEL_SHA256 = 'f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873'
CONTEXT_CODEC_SHA256 = 'c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27'
OBSERVATION_KERNEL_SHA256 = '502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c'
FULL_CONSUMER_SOURCE_SHA256 = 'f23a965ef3bc28a78ef06158efb9c7c50c375dc164d7e2840e5d350f51db58c3'
WIRE_CONTRACT_SHA256 = '1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed'
RESULT_SCHEMA = 'tsj-cohort-terminal-result-v2'
RESULT_MODE = 'OBSERVED_COHORT_TERMINAL_TELEMETRY'
RESULT_STATUS = 'COMPLETED_DIAGNOSTIC_ONLY'
QUALIFIED_PRIOR_SCHEMA = 'tsj-cohort-qualified-prior-v2'
MARKER_SCHEMA = 'tsj-cohort-terminal-marker-v2'
MARKER_STATUS = 'COMPLETED_DIAGNOSTIC'
CLEANUP_SCHEMA = 'tsj-cohort-terminal-cleanup-v1'
RUNTIME_IDENTITY_KEYS = ('runId', 'sourceManifestSha256', 'wireContractSha256', 'inputContextSha256')
RESULT_KEYS = ('schema', 'mode', 'status', 'runId', 'sourceManifestSha256', 'wireContractSha256', 'inputContextSha256', 'qualifiedPrior', 'originalCapture', 'cohort', 'arms', 'work', 'cleanup')
QUALIFIED_PRIOR_KEYS = ('schema', 'inputContextSha256', *_CONTEXT_PRIOR_KEYS)
MARKER_KEYS = ('schema', 'status', 'runId', 'sourceManifestSha256', 'wireContractSha256', 'inputContextSha256', 'resultSha256', 'resultBytes', 'elapsedNanos', 'finalExportNanos', 'totalGroups', 'totalUnits', 'maxGroupNanos', 'maximumUnitsPerGroup')
RECEIPT_KEYS = ('receiptPath', 'receiptBytes', 'receiptSha256', 'receiptOrdinal', 'lineageOrdinal')
CAPTURE_KEYS = ('schema', 'captureId', 'sourceManifestSha256', 'operation', 'receipt', 'request', 'seeds', 'ownerSettings', 'priorPromotedNetId', 'netIds', 'template', 'padIdentity', 'ownersReleased', 'contextRestored', 'cleanupNanos')
CAPTURE_REQUEST_KEYS = ('requestIndex', 'selectedAttempt', 'pass')
CAPTURE_OWNER_KEYS = ('policy', 'maximumExpansions', 'maximumVias', 'viaCost', 'policyTransitions', 'policyVias', 'branchOrderStrategy', 'netOrderStrategy', 'roleWidths', 'orderingStream')
TEMPLATE_KEYS = ('geometryBeforeSha256', 'geometryAfterSha256', 'effectsBeforeSha256', 'effectsAfterSha256', 'traceCount', 'holeCount')
CAPTURE_PAD_IDENTITY_KEYS = ('schema', 'padCount', 'padLandSha256')
COHORT_KEYS = ('netIds', 'requestedNetIds')
ARM_KEYS = ('armRole', 'armId', 'freshRun', 'observation', 'cleanup')
ARM_CLEANUP_KEYS = ('schema', 'adapterSessionAliasDropped', 'adapterSearchAliasDropped', 'adapterCandidateAliasDropped', 'adapterRouteResultAliasDropped', 'adapterBoardTemplateAliasesDropped', 'sourceGeometryBeforeSha256', 'sourceGeometryAfterSha256', 'contextRestored', 'cleanupNanos')
WORK_KEYS = ('elapsedCutoffNanos', 'cleanupNanos', 'groupsBeforeExport', 'wrapperGroupsBeforeExport', 'maxGroupNanosBeforeExport', 'maximumUnitsPerGroupBeforeExport', 'unitsBeforeExport', 'setupUnits', 'setupGroups', 'setupCopiedEntries', 'setupCopyChunks', 'originalCaptureOwnerUnits', 'originalCaptureWrapperUnits', 'originalCaptureCallbackUnits', 'originalCaptureDiagnosticUnits', 'originalCaptureGroups', 'originalCaptureCopiedEntries', 'originalCaptureCopyChunks', 'nullOwnerUnits', 'nullWrapperUnits', 'nullObserverUnits', 'observeOwnerUnits', 'observeWrapperUnits', 'observeObserverUnits', 'observerUnits', 'meterUnits', 'metadataUnits', 'metadataGroups', 'metadataCopiedEntries', 'metadataCopyChunks', 'copiedEntries', 'copyChunks', 'copyChunkLimit', 'copiedEntryLimit')
TOP_CLEANUP_KEYS = ('schema', 'originalCaptureOwnersReleased', 'originalCaptureContextRestored', 'nullArmAliasesDropped', 'observeArmAliasesDropped', 'sourceGeometryUnchanged', 'effectsScope', 'originalCaptureCopyEffectsUnchanged', 'contextRestored', 'cleanupNanos')
R8_P07_PAD_KEYS = ('record', 'group', 'padId', 'netId', 'componentId', 'x', 'y', 'role')
R8_PAD_PHYSICAL_KEYS = ('netId', 'componentId', 'x', 'y', 'role')
MAX_JSON_LINE = 1048576
MAX_MARKER_BYTES = 656
MAX_MARKER_TRANSPORT_BYTES = 722
MAX_OPERATION1_BYTES = 8 * 1024 * 1024
MAX_TOTAL_NS = 90000000000
MAX_CLEANUP_NS = 120000000000
MAX_GROUPS = 640
MAX_GROUP_NS = 5000000000
MAX_GROUP_UNITS = 32768
MAX_EXPANSIONS = 1000000
MAX_COPY_ENTRIES = 1048576
COPY_CHUNK = 4096
EXPORT_DIGEST_BYTES = 32
MIN_SIGNED_LONG = -2 ** 63
MAX_SIGNED_LONG = 2 ** 63 - 1
MAX_INT = 2 ** 31 - 1
MIN_INT = -2 ** 31
MAX_BODY_DEPTH = 64
MAX_BODY_VISITS = 1000000
UUID_RE = re.compile('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z')
SHA_RE = re.compile('[0-9a-f]{64}\\Z')

class ReaderError(ValueError):
    """Path-free validation error with a stable code."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith('BF_') else 'BF_' + code

def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)

def exact_keys(value, keys, code: str) -> None:
    need(type(value) is dict and set(value) == set(keys), code)

def ordered_keys(value, keys, code: str) -> None:
    exact_keys(value, keys, code)
    need(tuple(value) == tuple(keys), code + '_ORDER')

def _typed_equal(actual, expected) -> bool:
    """Compare JSON values without Python's bool/int equality coercion."""
    if type(actual) is not type(expected):
        return False
    if type(expected) is dict:
        return actual.keys() == expected.keys() and all((_typed_equal(actual[key], expected[key]) for key in expected))
    if type(expected) is list:
        return len(actual) == len(expected) and all((_typed_equal(left, right) for left, right in zip(actual, expected)))
    return actual == expected

def is_int(value, low: int=0, high: int=MAX_SIGNED_LONG) -> bool:
    return type(value) is int and low <= value <= high

def is_sha(value) -> bool:
    return type(value) is str and SHA_RE.fullmatch(value) is not None

def is_uuid(value) -> bool:
    return type(value) is str and UUID_RE.fullmatch(value) is not None

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result

def _reject_constant(_value):
    raise ValueError('nonfinite JSON value')

def _full_domain(value, code: str) -> None:
    visits = 0
    stack = [(value, 0)]
    while stack:
        node, depth = stack.pop()
        visits += 1
        need(visits <= MAX_BODY_VISITS and depth <= MAX_BODY_DEPTH, code)
        if node is None or type(node) is bool:
            continue
        if type(node) is int:
            need(MIN_SIGNED_LONG <= node <= MAX_SIGNED_LONG, code)
            continue
        if type(node) is str:
            need(len(node) <= 256 and all((32 <= ord(char) <= 126 for char in node)), code)
            continue
        if type(node) is list:
            stack.extend(((child, depth + 1) for child in reversed(node)))
            continue
        if type(node) is dict:
            for key, child in reversed(tuple(node.items())):
                need(type(key) is str and len(key) <= 256 and all((32 <= ord(char) <= 126 for char in key)), code)
                stack.append((child, depth + 1))
                visits += 1
                need(visits <= MAX_BODY_VISITS, code)
            continue
        raise ReaderError(code)

def _strict_json_line_core(raw: bytes, code: str) -> dict:
    need(type(raw) is bytes and 2 <= len(raw) <= MAX_JSON_LINE and raw.endswith(b'\n') and (b'\r' not in raw) and (not raw.startswith(b'\xef\xbb\xbf')), code)
    body = raw[:-1]
    need(body and b'\n' not in body, code)
    try:
        text = body.decode('utf-8', errors='strict')
        value = json.loads(text, object_pairs_hook=_pairs, parse_constant=_reject_constant)
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError, RecursionError):
        raise ReaderError(code) from None
    need(type(value) is dict, code)
    _full_domain(value, code)
    return value

def _canonical_json_line(value, raw: bytes, max_bytes: int, code: str) -> int:
    need(type(raw) is bytes and len(raw) >= 2 and (len(raw) <= max_bytes) and raw.endswith(b'\n') and (b'\r' not in raw) and (not raw.startswith(b'\xef\xbb\xbf')) and (b'\n' not in raw[:-1]), code + '_FRAMING')
    _full_domain(value, code + '_DOMAIN')
    try:
        canonical = json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(',', ':'), sort_keys=False).encode('ascii') + b'\n'
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise ReaderError(code + '_ENCODING') from None
    need(raw == canonical, code + '_CANONICAL')
    return len(raw)

def _validate_context_order(context) -> None:
    ordered_keys(context, _CONTEXT_TOP_KEYS, 'CONTEXT_KEYS')
    projection = context['nativeProjection']
    ordered_keys(projection, _NATIVE_PROJECTION_KEYS, 'NATIVE_PROJECTION_KEYS')
    prior = projection['prior']
    ordered_keys(prior, _CONTEXT_PRIOR_KEYS, 'PRIOR_KEYS')
    ordered_keys(prior['priorOriginalReceipt'], _CONTEXT_PRIOR_RECEIPT_KEYS, 'PRIOR_RECEIPT_KEYS')
    ordered_keys(prior['trialDisposition'], _TRIAL_DISPOSITION_KEYS, 'TRIAL_DISPOSITION_KEYS')
    capture = projection['capture']
    ordered_keys(capture, ('cohortNetIds', 'capturedNetIds', 'trialDisposition', 'controlDisposition', 'trialOutcome', 'passIndex', 'failedNetId', 'priorPromotedNetId', 'liveSearchIndex'), 'CAPTURE_KEYS')
    ordered_keys(projection['ownerSettings'], _OWNER_KEYS, 'OWNER_KEYS')
    ordered_keys(projection['seedValues'], _SEED_KEYS, 'SEED_KEYS')
    source_identity = projection['sourceIdentity']
    ordered_keys(source_identity, _SOURCE_IDENTITY_KEYS, 'SOURCE_IDENTITY_KEYS')
    ordered_keys(source_identity['padLandIdentity'], _CONTEXT_PAD_IDENTITY_KEYS, 'PAD_IDENTITY_KEYS')
    physical = context['physicalContext']
    ordered_keys(physical, _PHYSICAL_CONTEXT_KEYS, 'PHYSICAL_CONTEXT_KEYS')
    need(type(physical['padLands']) is list and type(physical['qualifiedP07Pads']) is list, 'PHYSICAL_CONTEXT_ROWS')
    for row in physical['padLands']:
        ordered_keys(row, _PAD_LAND_KEYS, 'PAD_LAND_KEYS')
    for row in physical['qualifiedP07Pads']:
        ordered_keys(row, _P07_PAD_KEYS, 'P07_PAD_KEYS')

def _validate_runtime_identity(runtime_identity, context) -> bytes:
    ordered_keys(runtime_identity, RUNTIME_IDENTITY_KEYS, 'RUNTIME_IDENTITY')
    need(is_uuid(runtime_identity['runId']) and all((is_sha(runtime_identity[name]) for name in ('sourceManifestSha256', 'wireContractSha256', 'inputContextSha256'))), 'RUNTIME_IDENTITY_FIELDS')
    need(runtime_identity['wireContractSha256'] == WIRE_CONTRACT_SHA256, 'RUNTIME_WIRE_IDENTITY')
    try:
        canonical = _canonical_context(context)
        measured = _context_sha256(context)
    except _ContextCodecError as failure:
        raise ReaderError(failure.code) from None
    except RecursionError:
        raise ReaderError('RUNTIME_CONTEXT_DEPTH') from None
    need(type(canonical) is bytes and len(canonical) <= MAX_JSON_LINE and (sha(canonical) == measured) and (measured == runtime_identity['inputContextSha256']), 'RUNTIME_CONTEXT_BINDING')
    _validate_context_order(context)
    return canonical

def _prior_semantics(context):
    projection = context['nativeProjection']
    capture = projection['capture']
    settings = projection['ownerSettings']
    trial = capture['trialDisposition']
    captured_ids = capture['capturedNetIds']
    expected_owner = {'policy': settings['boardMode'], 'maximumExpansions': settings['maxExpansions'], 'expandedAtFailure': settings['expandedAtFailure'], 'maximumVias': settings['maxVias'], 'viaCost': settings['viaCost'], 'policyTransitions': settings['maxTransitions'], 'policyVias': settings['maxVias'], 'branchOrderStrategy': settings['branchStrategy'], 'netOrderStrategy': settings['netStrategy'], 'roleWidths': settings['roleWidthsEnabled'], 'orderingStream': settings['orderingStreamEnabled']}
    need(capture['passIndex'] == 2 and type(captured_ids) is list and (len(captured_ids) == 60) and (len(set(captured_ids)) == 60) and (capture['failedNetId'] in captured_ids) and (capture['priorPromotedNetId'] in captured_ids) and is_int(capture['liveSearchIndex'], 1, 60) and (captured_ids[capture['liveSearchIndex'] - 1] == capture['failedNetId']), 'PRIOR_VECTOR_BINDING')
    need(capture['controlDisposition'] == 'NO_PATH' and capture['trialOutcome'] == 'CAP_REJECTED' and (trial['status'] == 'RUN') and (trial['reason'] == 'TRIAL_EXECUTED') and (trial['feedbackValidated'] is True) and (trial['cohortNetIds'] == capture['cohortNetIds']) and (trial['capturedFailedNetId'] == capture['cohortNetIds'][0]) and (trial['controlFailedNetId'] == capture['cohortNetIds'][1]), 'PRIOR_COHORT_BINDING')
    need(capture['priorPromotedNetId'] == 'D_SENSE', 'PRIOR_PROMOTION_BINDING')
    captured = {'owner': expected_owner, 'netIds': captured_ids, 'failedNetId': capture['failedNetId'], 'priorPromotedNetId': capture['priorPromotedNetId']}
    return (context['physicalContext']['padLands'], captured)

def _prior_grid_dimensions(context):
    physical = context['physicalContext']
    width, height = (physical['gridWidth'], physical['gridHeight'])
    need(is_int(width, 1, 250000) and is_int(height, 1, 250000) and (width * height <= 250000), 'PRIOR_GRID_BINDING')
    return (width, height)

def _expected_prior_ref(context, runtime_identity):
    prior = context['nativeProjection']['prior']
    result = {'schema': QUALIFIED_PRIOR_SCHEMA, 'inputContextSha256': runtime_identity['inputContextSha256']}
    result.update(prior)
    ordered_keys(result, QUALIFIED_PRIOR_KEYS, 'QUALIFIED_PRIOR_SCHEMA')
    return result

def _pad_lookup(pads):
    need(type(pads) is list and len(pads) == 246, 'PAD_CENSUS')
    by_id = {}
    by_net = {}
    for row in pads:
        need(type(row) is dict, 'PAD_ROW')
        pad_id, net_id = (row.get('padId'), row.get('netId'))
        x, y = (row.get('x'), row.get('y'))
        need(type(pad_id) is str and bool(pad_id) and (type(net_id) is str) and bool(net_id) and is_int(x, MIN_INT, MAX_INT) and is_int(y, MIN_INT, MAX_INT) and (pad_id not in by_id), 'PAD_ROW_BINDING')
        by_id[pad_id] = (net_id, x, y)
        by_net.setdefault(net_id, set()).add(pad_id)
    return (by_id, by_net)

def _validate_original_capture(value, context, captured, op1_raw, runtime_identity):
    exact_keys(value, CAPTURE_KEYS, 'ORIGINAL_CAPTURE_SCHEMA')
    need(value['schema'] == 'tsj-cohort-terminal-original-capture-v1' and is_uuid(value['captureId']) and (value['captureId'] != runtime_identity['runId']) and (value['sourceManifestSha256'] == runtime_identity['sourceManifestSha256']) and (value['operation'] == 'AUTHORED'), 'ORIGINAL_CAPTURE_IDENTITY')
    receipt = value['receipt']
    exact_keys(receipt, RECEIPT_KEYS, 'ORIGINAL_RECEIPT_SCHEMA')
    need(receipt['receiptPath'] == 'op-1.jsonl' and is_int(receipt['receiptBytes'], len(op1_raw), len(op1_raw)) and (receipt['receiptSha256'] == sha(op1_raw)) and is_int(receipt['receiptOrdinal'], 1, MAX_INT) and is_int(receipt['lineageOrdinal'], 1, MAX_INT) and (receipt['receiptOrdinal'] == 307) and (receipt['lineageOrdinal'] == 77), 'ORIGINAL_RECEIPT_BINDING')
    exact_keys(value['request'], CAPTURE_REQUEST_KEYS, 'ORIGINAL_REQUEST_SCHEMA')
    need(_typed_equal(value['request'], {'requestIndex': 0, 'selectedAttempt': 4, 'pass': 2}), 'ORIGINAL_REQUEST_BINDING')
    projection = context['nativeProjection']
    need(_typed_equal(value['seeds'], projection['seedValues']), 'ORIGINAL_SEED_BINDING')
    settings = projection['ownerSettings']
    expected_owner = {'policy': settings['boardMode'], 'maximumExpansions': settings['maxExpansions'], 'maximumVias': settings['maxVias'], 'viaCost': settings['viaCost'], 'policyTransitions': settings['maxTransitions'], 'policyVias': settings['maxVias'], 'branchOrderStrategy': settings['branchStrategy'], 'netOrderStrategy': settings['netStrategy'], 'roleWidths': settings['roleWidthsEnabled'], 'orderingStream': settings['orderingStreamEnabled']}
    exact_keys(value['ownerSettings'], CAPTURE_OWNER_KEYS, 'ORIGINAL_OWNER_SCHEMA')
    need(_typed_equal(value['ownerSettings'], expected_owner) and value['priorPromotedNetId'] == captured['priorPromotedNetId'] and (type(value['netIds']) is list) and (len(value['netIds']) == 60) and all((type(net_id) is str for net_id in value['netIds'])) and (value['netIds'] == captured['netIds']) and (len(set(value['netIds'])) == 60), 'ORIGINAL_OWNER_BINDING')
    source_identity = projection['sourceIdentity']
    template = value['template']
    exact_keys(template, TEMPLATE_KEYS, 'ORIGINAL_TEMPLATE_SCHEMA')
    expected_template = {'geometryBeforeSha256': source_identity['geometryBeforeSha256'], 'geometryAfterSha256': source_identity['geometryAfterSha256'], 'effectsBeforeSha256': source_identity['effectsBeforeSha256'], 'effectsAfterSha256': source_identity['effectsAfterSha256'], 'traceCount': source_identity['traceCount'], 'holeCount': source_identity['holeCount']}
    need(_typed_equal(template, expected_template) and template['traceCount'] == 0 and (template['holeCount'] == 0) and (template['effectsBeforeSha256'] == template['effectsAfterSha256']), 'ORIGINAL_TEMPLATE_BINDING')
    pad_identity = value['padIdentity']
    exact_keys(pad_identity, CAPTURE_PAD_IDENTITY_KEYS, 'ORIGINAL_PAD_SCHEMA')
    prior_pad = source_identity['padLandIdentity']
    need(pad_identity == {'schema': prior_pad['format'], 'padCount': prior_pad['rowCount'], 'padLandSha256': prior_pad['sha256']} and pad_identity['schema'] == 'TSJ_PAD_LAND_ASCII_V1' and (pad_identity['padCount'] == 246), 'ORIGINAL_PAD_BINDING')
    need(all((is_sha(template[name]) for name in ('geometryBeforeSha256', 'geometryAfterSha256', 'effectsBeforeSha256', 'effectsAfterSha256'))), 'ORIGINAL_TEMPLATE_DIGEST')
    need(value['ownersReleased'] is True and value['contextRestored'] is True and is_int(value['cleanupNanos'], 0, MAX_CLEANUP_NS), 'ORIGINAL_CAPTURE_CLEANUP')
    return receipt

def _validate_arm_cleanup(value, original_template=None):
    exact_keys(value, ARM_CLEANUP_KEYS, 'ARM_CLEANUP_SCHEMA')
    need(value['schema'] == 'tsj-cohort-terminal-arm-cleanup-v1' and all((value[name] is True for name in ('adapterSessionAliasDropped', 'adapterSearchAliasDropped', 'adapterCandidateAliasDropped', 'adapterRouteResultAliasDropped', 'adapterBoardTemplateAliasesDropped', 'contextRestored'))) and all((is_sha(value[name]) for name in ('sourceGeometryBeforeSha256', 'sourceGeometryAfterSha256'))) and (value['sourceGeometryBeforeSha256'] == value['sourceGeometryAfterSha256']) and (original_template is None or value['sourceGeometryBeforeSha256'] == original_template['geometryBeforeSha256'] == original_template['geometryAfterSha256']) and is_int(value['cleanupNanos'], 0, MAX_CLEANUP_NS), 'ARM_CLEANUP_BINDING')
    return value

def _validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net, expected_grid_dimensions):
    try:
        return _ObservationV2Kernel.validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net, expected_grid_dimensions)
    except _ObservationV2Error as failure:
        raise ReaderError(failure.code) from None

def _validate_observation(value, arm_role, fresh_run, facts, pads_by_id, pads_by_net, expected_grid_dimensions):
    try:
        return _ObservationV2Kernel.validate_observation(value, arm_role, fresh_run['work']['advanceCalls'], fresh_run['ordering']['requestedNetIds'], fresh_run['ordering']['traversedNetIds'], pads_by_id, pads_by_net, expected_grid_dimensions, facts['outcome'], facts['terminalFailure'])
    except _ObservationV2Error as failure:
        raise ReaderError(failure.code) from None

def _validate_arm(value, arm_role, cohort_ids, captured, pads_by_id, pads_by_net, original_template, runtime_identity, expected_grid_dimensions):
    exact_keys(value, ARM_KEYS, 'ARM_SCHEMA')
    need(value['armRole'] == arm_role and is_uuid(value['armId']) and (value['armId'] != runtime_identity['runId']), 'ARM_IDENTITY')
    fresh_run = value['freshRun']
    try:
        facts = _validate_prior_run(fresh_run, 'OBSERVED_FAILURE_COHORT_TRIAL', captured, cohort_ids)
    except _PriorReaderError as failure:
        raise ReaderError(failure.code) from None
    observation_facts = _validate_observation(value['observation'], arm_role, fresh_run, facts, pads_by_id, pads_by_net, expected_grid_dimensions)
    cleanup = _validate_arm_cleanup(value['cleanup'], original_template)
    need(fresh_run['kind'] == 'OBSERVED_FAILURE_COHORT_TRIAL', 'ARM_RUN_KIND')
    return {'freshRun': fresh_run, 'observation': value['observation'], 'facts': facts, 'observationFacts': observation_facts, 'cleanup': cleanup}

def _validate_work(value, original_capture, arms, observations):
    ordered_keys(value, WORK_KEYS, 'WORK_SCHEMA')
    copy_names = {'setupCopiedEntries', 'originalCaptureCopiedEntries', 'metadataCopiedEntries', 'copiedEntries'}
    chunk_names = {'setupCopyChunks', 'originalCaptureCopyChunks', 'metadataCopyChunks', 'copyChunks', 'copyChunkLimit'}
    group_names = {'groupsBeforeExport', 'wrapperGroupsBeforeExport', 'setupGroups', 'originalCaptureGroups', 'metadataGroups'}
    for name in WORK_KEYS:
        if name == 'elapsedCutoffNanos':
            okay = is_int(value[name], 1, MAX_TOTAL_NS)
        elif name == 'cleanupNanos':
            okay = is_int(value[name], 0, MAX_CLEANUP_NS)
        elif name in copy_names or name in chunk_names:
            okay = is_int(value[name], 0, MAX_COPY_ENTRIES)
        elif name in group_names:
            okay = is_int(value[name], 0, MAX_GROUPS)
        elif name == 'maxGroupNanosBeforeExport':
            okay = is_int(value[name], 1, MAX_GROUP_NS)
        elif name == 'maximumUnitsPerGroupBeforeExport':
            okay = is_int(value[name], 1, MAX_GROUP_UNITS)
        else:
            okay = is_int(value[name], 0, MAX_GROUP_UNITS * MAX_GROUPS)
        need(okay, 'WORK_INTEGER')
    need(0 < value['elapsedCutoffNanos'] <= MAX_TOTAL_NS and value['setupUnits'] > 0 and (value['setupGroups'] > 0) and (value['metadataUnits'] > 0) and (value['metadataGroups'] > 0) and (value['cleanupNanos'] <= MAX_CLEANUP_NS) and (value['groupsBeforeExport'] + 1 <= MAX_GROUPS) and (0 < value['wrapperGroupsBeforeExport'] <= value['groupsBeforeExport']) and (value['maxGroupNanosBeforeExport'] <= MAX_GROUP_NS) and (value['maximumUnitsPerGroupBeforeExport'] <= MAX_GROUP_UNITS) and (value['unitsBeforeExport'] <= value['groupsBeforeExport'] * MAX_GROUP_UNITS) and (value['copyChunkLimit'] == COPY_CHUNK) and (value['copiedEntryLimit'] == MAX_COPY_ENTRIES), 'WORK_LIMITS')
    null_run, observe_run = (arms['null']['freshRun'], arms['observe']['freshRun'])
    null_obs, observe_obs = (observations['null'], observations['observe'])
    need(value['nullOwnerUnits'] == null_run['work']['ownerUnits'] and value['nullWrapperUnits'] == null_run['work']['wrapperUnits'] and (value['nullObserverUnits'] == null_obs['observerUnits'] == 0) and (value['observeOwnerUnits'] == observe_run['work']['ownerUnits']) and (value['observeWrapperUnits'] == observe_run['work']['wrapperUnits']) and (value['observeObserverUnits'] == observe_obs['observerUnits']) and (value['observerUnits'] == value['nullObserverUnits'] + value['observeObserverUnits']), 'WORK_ARM_BINDING')
    need(value['originalCaptureGroups'] >= 1 and value['originalCaptureCopiedEntries'] >= 0 and (value['originalCaptureCopyChunks'] <= value['originalCaptureCopiedEntries']), 'WORK_CAPTURE_GROUPS')
    expected_units = sum((value[name] for name in ('setupUnits', 'originalCaptureOwnerUnits', 'originalCaptureWrapperUnits', 'originalCaptureCallbackUnits', 'originalCaptureDiagnosticUnits', 'nullOwnerUnits', 'nullWrapperUnits', 'nullObserverUnits', 'observeOwnerUnits', 'observeWrapperUnits', 'observeObserverUnits', 'metadataUnits')))
    need(value['meterUnits'] == expected_units == value['unitsBeforeExport'], 'WORK_UNIT_EQUATION')
    expected_groups = value['setupGroups'] + value['originalCaptureGroups'] + null_run['work']['groups'] + observe_run['work']['groups'] + value['metadataGroups']
    need(value['groupsBeforeExport'] == expected_groups, 'WORK_GROUP_EQUATION')
    expected_entries = value['setupCopiedEntries'] + value['originalCaptureCopiedEntries'] + null_run['work']['copyEntries'] + observe_run['work']['copyEntries'] + observe_obs['copiedEntries'] + value['metadataCopiedEntries']
    expected_chunks = value['setupCopyChunks'] + value['originalCaptureCopyChunks'] + null_run['work']['copyChunks'] + observe_run['work']['copyChunks'] + observe_obs['copyChunks'] + value['metadataCopyChunks']
    need(value['copiedEntries'] == expected_entries and value['copyChunks'] == expected_chunks and (value['copiedEntries'] <= MAX_COPY_ENTRIES) and (value['copyChunks'] <= value['copiedEntries']) and (value['copiedEntries'] == 0 or value['copyChunks'] >= (value['copiedEntries'] + COPY_CHUNK - 1) // COPY_CHUNK), 'WORK_COPY_EQUATION')
    need(value['maxGroupNanosBeforeExport'] >= max(null_run['work']['maxGroupNanos'], observe_run['work']['maxGroupNanos']) and value['maximumUnitsPerGroupBeforeExport'] >= max(null_run['work']['maximumUnitsPerGroup'], observe_run['work']['maximumUnitsPerGroup']), 'WORK_MAXIMA')
    return value

def _validate_top_cleanup(value, capture, arms, work):
    exact_keys(value, TOP_CLEANUP_KEYS, 'TOP_CLEANUP_SCHEMA')
    null_cleanup, observe_cleanup = (arms['null']['cleanup'], arms['observe']['cleanup'])
    geometry = capture['template']['geometryBeforeSha256'] == capture['template']['geometryAfterSha256'] == null_cleanup['sourceGeometryBeforeSha256'] == null_cleanup['sourceGeometryAfterSha256'] == observe_cleanup['sourceGeometryBeforeSha256'] == observe_cleanup['sourceGeometryAfterSha256']
    copy_effects_unchanged = capture['template']['effectsBeforeSha256'] == capture['template']['effectsAfterSha256']
    aliases = lambda arm: all((arm['cleanup'][name] is True for name in ('adapterSessionAliasDropped', 'adapterSearchAliasDropped', 'adapterCandidateAliasDropped', 'adapterRouteResultAliasDropped', 'adapterBoardTemplateAliasesDropped')))
    expected = {'schema': CLEANUP_SCHEMA, 'originalCaptureOwnersReleased': capture['ownersReleased'], 'originalCaptureContextRestored': capture['contextRestored'], 'nullArmAliasesDropped': aliases(arms['null']), 'observeArmAliasesDropped': aliases(arms['observe']), 'sourceGeometryUnchanged': geometry, 'effectsScope': 'ORIGINAL_CAPTURE_COPY_ONLY', 'originalCaptureCopyEffectsUnchanged': copy_effects_unchanged, 'contextRestored': capture['contextRestored'] and null_cleanup['contextRestored'] and observe_cleanup['contextRestored'], 'cleanupNanos': work['cleanupNanos']}
    need(geometry is True and copy_effects_unchanged is True, 'TOP_CLEANUP_UNCHANGED')
    need(value == expected and all((type(value[name]) is bool for name in ('originalCaptureOwnersReleased', 'originalCaptureContextRestored', 'nullArmAliasesDropped', 'observeArmAliasesDropped', 'sourceGeometryUnchanged', 'originalCaptureCopyEffectsUnchanged', 'contextRestored'))) and is_int(value['cleanupNanos'], 0, MAX_CLEANUP_NS), 'TOP_CLEANUP_BINDING')
    minimum_cleanup = capture['cleanupNanos'] + null_cleanup['cleanupNanos'] + observe_cleanup['cleanupNanos']
    need(value['cleanupNanos'] >= minimum_cleanup, 'TOP_CLEANUP_ACCOUNTING')

def _parity_projection(run):
    return {'initial': run['initial'], 'ordering': run['ordering'], 'outcome': run['outcome'], 'terminalFailure': run['terminalFailure'], 'finalizers': run['finalizers'], 'layout': run['layout'], 'advanceCalls': run['work']['advanceCalls']}

def _validate_finite_expectation(arms):
    for name in ('null', 'observe'):
        run = arms[name]['freshRun']
        facts = arms[name]['facts']
        observation = arms[name]['observationFacts']
        observation_dto = arms[name]['observation']
        need(facts['outcome']['reason'] == 'D_COIL_LOW:BRANCH_SEARCH_LIMIT' and facts['outcome']['expanded'] == 419905 and (len(run['ordering']['traversedNetIds']) == 43) and (observation['state'] == ('NOT_ENABLED' if name == 'null' else 'FIRST_NON_FOUND')), 'FINITE_REFERENCE_MISMATCH')
        if name == 'null':
            need(observation_dto['selectedSnapshot'] is None, 'FINITE_REFERENCE_MISMATCH')
        else:
            selected = observation_dto['selectedSnapshot']
            need(type(selected) is dict and selected['netId'] == 'D_COIL_LOW' and (selected['terminalReason'] == 'BRANCH_SEARCH_LIMIT') and (selected['totalExpanded'] == 419905), 'FINITE_REFERENCE_MISMATCH')

def _validate_result_semantics(doc, context, runtime_identity, op1_raw, result_raw):
    _full_domain(doc, 'RESULT_ASCII')
    ordered_keys(doc, RESULT_KEYS, 'RESULT_SCHEMA')
    need(doc['schema'] == RESULT_SCHEMA and doc['mode'] == RESULT_MODE and (doc['status'] == RESULT_STATUS) and (doc['runId'] == runtime_identity['runId']) and (doc['sourceManifestSha256'] == runtime_identity['sourceManifestSha256']) and (doc['wireContractSha256'] == runtime_identity['wireContractSha256']) and (doc['inputContextSha256'] == runtime_identity['inputContextSha256']), 'RESULT_IDENTITY')
    prior_expected = _expected_prior_ref(context, runtime_identity)
    ordered_keys(doc['qualifiedPrior'], QUALIFIED_PRIOR_KEYS, 'QUALIFIED_PRIOR_SCHEMA')
    ordered_keys(doc['qualifiedPrior']['priorOriginalReceipt'], RECEIPT_KEYS, 'PRIOR_RECEIPT_SCHEMA')
    need(_typed_equal(doc['qualifiedPrior'], prior_expected), 'QUALIFIED_PRIOR_BINDING')
    pad_rows, captured = _prior_semantics(context)
    capture = doc['originalCapture']
    receipt = _validate_original_capture(capture, context, captured, op1_raw, runtime_identity)
    expected_grid = _prior_grid_dimensions(context)
    cohort = doc['cohort']
    exact_keys(cohort, COHORT_KEYS, 'COHORT_SCHEMA')
    prior_disposition = prior_expected['trialDisposition']
    cohort_ids = prior_disposition['cohortNetIds']
    requested = _stable_cohort_order(captured['netIds'], cohort_ids)
    need(cohort['netIds'] == cohort_ids and cohort['requestedNetIds'] == requested and (prior_disposition['requestedNetIds'] == requested), 'COHORT_ORDER_BINDING')
    arms_value = doc['arms']
    exact_keys(arms_value, ('null', 'observe'), 'ARMS_SCHEMA')
    arm_ids = [arms_value[name].get('armId') if type(arms_value[name]) is dict else None for name in ('null', 'observe')]
    need(all((is_uuid(arm_id) for arm_id in arm_ids)), 'ARM_IDENTITY')
    need(len(set(arm_ids + [runtime_identity['runId'], capture['captureId']])) == 4, 'ARM_ID_DUPLICATE')
    pads_by_id, pads_by_net = _pad_lookup(pad_rows)
    need(len(pads_by_net) == 60 and set(pads_by_net) == set(captured['netIds']) and all((pads_by_net[net] for net in captured['netIds'])), 'PAD_NET_BIJECTION')
    arms = {}
    observations = {}
    for name, role in (('null', 'NULL'), ('observe', 'OBSERVE')):
        arms[name] = _validate_arm(arms_value[name], role, cohort_ids, captured, pads_by_id, pads_by_net, capture['template'], runtime_identity, expected_grid)
        observations[name] = arms[name]['observationFacts']
    null_run, observe_run = (arms['null']['freshRun'], arms['observe']['freshRun'])
    need(_parity_projection(null_run) == _parity_projection(observe_run), 'ARM_PARITY')
    _validate_finite_expectation(arms)
    work = _validate_work(doc['work'], capture, arms, observations)
    need(work['metadataUnits'] >= _result_tree_minimum_units(doc), 'WORK_METADATA_TREE')
    need(receipt['receiptOrdinal'] == 307 and receipt['lineageOrdinal'] == 77, 'ORIGINAL_RECEIPT_PRECONDITION')
    _validate_top_cleanup(doc['cleanup'], capture, arms, work)
    return {'arms': arms, 'observations': observations, 'work': work, 'capture': capture, 'receipt': receipt}

def validate_marker_line(marker, marker_raw: bytes) -> int:
    need(type(marker) is dict, 'MARKER_SCHEMA')
    ordered_keys(marker, MARKER_KEYS, 'MARKER_SCHEMA')
    size = _canonical_json_line(marker, marker_raw, MAX_MARKER_BYTES, 'MARKER')
    need(size <= MAX_MARKER_TRANSPORT_BYTES, 'MARKER_TRANSPORT_BOUND')
    string_fields = ('schema', 'status', 'runId', 'sourceManifestSha256', 'wireContractSha256', 'inputContextSha256', 'resultSha256')
    need(all((type(marker[name]) is str for name in string_fields)) and marker['schema'] == MARKER_SCHEMA and (marker['status'] == MARKER_STATUS) and is_uuid(marker['runId']) and all((is_sha(marker[name]) for name in ('sourceManifestSha256', 'wireContractSha256', 'inputContextSha256', 'resultSha256'))), 'MARKER_STRINGS')
    numeric_fields = ('resultBytes', 'elapsedNanos', 'finalExportNanos', 'totalGroups', 'totalUnits', 'maxGroupNanos', 'maximumUnitsPerGroup')
    need(all((type(marker[name]) is int and 0 <= marker[name] <= MAX_SIGNED_LONG for name in numeric_fields)), 'MARKER_NUMBERS')
    return size

def _validate_marker_binding(marker, marker_raw, result_raw, doc, runtime_identity):
    validate_marker_line(marker, marker_raw)
    work = doc['work']
    export_units = 5 * ((len(result_raw) + COPY_CHUNK - 1) // COPY_CHUNK) + 3
    need(marker['runId'] == runtime_identity['runId'] and marker['sourceManifestSha256'] == runtime_identity['sourceManifestSha256'] and (marker['wireContractSha256'] == runtime_identity['wireContractSha256']) and (marker['inputContextSha256'] == runtime_identity['inputContextSha256']) and (marker['resultSha256'] == sha(result_raw)) and is_int(marker['resultBytes'], len(result_raw), len(result_raw)) and is_int(marker['elapsedNanos'], 1, MAX_TOTAL_NS) and is_int(marker['finalExportNanos'], 1, MAX_GROUP_NS) and is_int(marker['totalGroups'], work['groupsBeforeExport'] + 1, work['groupsBeforeExport'] + 1) and is_int(marker['totalUnits'], work['unitsBeforeExport'] + export_units, work['unitsBeforeExport'] + export_units) and is_int(marker['maxGroupNanos'], max(work['maxGroupNanosBeforeExport'], marker['finalExportNanos']), max(work['maxGroupNanosBeforeExport'], marker['finalExportNanos'])) and is_int(marker['maximumUnitsPerGroup'], max(work['maximumUnitsPerGroupBeforeExport'], export_units), max(work['maximumUnitsPerGroupBeforeExport'], export_units)) and (export_units <= MAX_GROUP_UNITS) and (marker['elapsedNanos'] >= work['elapsedCutoffNanos'] + marker['finalExportNanos']), 'MARKER_BINDING')
    return marker

def _export_copy_deltas(result_bytes, marker_bytes):
    need(is_int(result_bytes, 2, MAX_JSON_LINE) and is_int(marker_bytes, 2, MAX_MARKER_BYTES), 'EXPORT_COPY_LENGTHS')
    result_chunks = (result_bytes + COPY_CHUNK - 1) // COPY_CHUNK
    return (2 * result_bytes + EXPORT_DIGEST_BYTES + marker_bytes, 2 * result_chunks + 2, result_chunks)

def derive_export_copy_reconciliation(result_doc, result_raw, marker_doc, marker_raw):
    """Derive bounded export demand; this is not a Meter observation."""
    need(type(result_doc) is dict, 'EXPORT_RESULT_SCHEMA')
    ordered_keys(result_doc, RESULT_KEYS, 'EXPORT_RESULT_SCHEMA')
    result_bytes = _canonical_json_line(result_doc, result_raw, MAX_JSON_LINE, 'EXPORT_RESULT')
    need(result_doc['schema'] == RESULT_SCHEMA and result_doc['mode'] == RESULT_MODE and (result_doc['status'] == RESULT_STATUS) and is_uuid(result_doc['runId']) and is_sha(result_doc['sourceManifestSha256']) and (result_doc['wireContractSha256'] == WIRE_CONTRACT_SHA256) and is_sha(result_doc['inputContextSha256']), 'EXPORT_RESULT_IDENTITY')
    marker_bytes = validate_marker_line(marker_doc, marker_raw)
    need(marker_doc['runId'] == result_doc['runId'] and marker_doc['sourceManifestSha256'] == result_doc['sourceManifestSha256'] and (marker_doc['wireContractSha256'] == result_doc['wireContractSha256']) and (marker_doc['inputContextSha256'] == result_doc['inputContextSha256']) and (marker_doc['resultBytes'] == result_bytes) and (marker_doc['resultSha256'] == sha(result_raw)), 'EXPORT_MARKER_RESULT_BINDING')
    work = result_doc['work']
    ordered_keys(work, WORK_KEYS, 'EXPORT_WORK_SCHEMA')
    pre_entries, pre_chunks = (work['copiedEntries'], work['copyChunks'])
    need(is_int(pre_entries, 0, MAX_COPY_ENTRIES) and is_int(pre_chunks, 0, MAX_COPY_ENTRIES) and (pre_chunks <= pre_entries) and (work['copiedEntryLimit'] == MAX_COPY_ENTRIES) and (work['copyChunkLimit'] == COPY_CHUNK), 'EXPORT_PRECOPY_BINDING')
    export_entries, export_chunks, result_chunks = _export_copy_deltas(result_bytes, marker_bytes)
    required_entries = pre_entries + export_entries
    required_chunks = pre_chunks + export_chunks
    need(required_entries <= MAX_COPY_ENTRIES and required_chunks <= MAX_COPY_ENTRIES and (required_chunks <= required_entries), 'EXPORT_COPY_CAPACITY')
    return {'status': 'EXPORT_COPY_RECONCILIATION_DERIVED_ONLY', 'resultBytes': result_bytes, 'resultChunks': result_chunks, 'markerBytes': marker_bytes, 'derivedExportCopiedEntries': export_entries, 'derivedExportCopyChunks': export_chunks, 'requiredCumulativeCopiedEntries': required_entries, 'requiredCumulativeCopyChunks': required_chunks, 'scope': 'SOURCE_BOUND_DERIVATION_NOT_METER_OBSERVATION'}

def _r8_pad_projection(rows, code):
    need(type(rows) is list, code + '_ROWS')
    by_pad_id = {}
    for row in rows:
        exact_keys(row, R8_P07_PAD_KEYS, code + '_SCHEMA')
        pad_id, net_id = (row['padId'], row['netId'])
        component_id, role = (row['componentId'], row['role'])
        x, y = (row['x'], row['y'])
        need(all((type(value) is str and bool(value) for value in (pad_id, net_id, component_id, role))) and is_int(x, MIN_INT, MAX_INT) and is_int(y, MIN_INT, MAX_INT), code + '_PHYSICAL_FIELDS')
        _full_domain(pad_id, code + '_PHYSICAL_FIELDS')
        _full_domain(net_id, code + '_PHYSICAL_FIELDS')
        _full_domain(component_id, code + '_PHYSICAL_FIELDS')
        _full_domain(role, code + '_PHYSICAL_FIELDS')
        need(pad_id not in by_pad_id, code + '_DUPLICATE_PAD_ID')
        by_pad_id[pad_id] = tuple((row[name] for name in R8_PAD_PHYSICAL_KEYS))
    return by_pad_id

def derive_original_group_reconciliation(result_doc, op1_raw, qualified_pads, expected_grid_dimensions):
    """Derive mandatory owner-group and physical-PAD reconciliation."""
    need(type(result_doc) is dict, 'R8_RESULT_SCHEMA')
    ordered_keys(result_doc, RESULT_KEYS, 'R8_RESULT_SCHEMA')
    need(result_doc['schema'] == RESULT_SCHEMA and result_doc['mode'] == RESULT_MODE and (result_doc['status'] == RESULT_STATUS) and is_uuid(result_doc['runId']) and is_sha(result_doc['sourceManifestSha256']) and (result_doc['wireContractSha256'] == WIRE_CONTRACT_SHA256) and is_sha(result_doc['inputContextSha256']), 'R8_RESULT_IDENTITY')
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= MAX_OPERATION1_BYTES, 'R8_OPERATION1_BYTES')
    work = result_doc['work']
    ordered_keys(work, WORK_KEYS, 'R8_WORK_SCHEMA')
    elapsed_envelope = work['elapsedCutoffNanos']
    group_envelope = work['maxGroupNanosBeforeExport']
    original_capture_groups = work['originalCaptureGroups']
    groups_before_export = work['groupsBeforeExport']
    wrapper_groups = work['wrapperGroupsBeforeExport']
    need(is_int(elapsed_envelope, 1, MAX_TOTAL_NS) and is_int(group_envelope, 1, MAX_GROUP_NS) and is_int(original_capture_groups, 1, MAX_GROUPS) and is_int(groups_before_export, 1, MAX_GROUPS - 1) and is_int(wrapper_groups, 1, MAX_GROUPS) and (wrapper_groups <= groups_before_export), 'R8_WORK_GROUP_BOUNDS')
    entry = {'elapsedToCloseNanos': elapsed_envelope, 'maxFullGroupNanos': group_envelope}
    try:
        parsed = _validate_operation(op1_raw, entry, result_doc['sourceManifestSha256'], result_doc['wireContractSha256'], 1, result_doc['runId'], 'AUTHORED')
    except Exception as failure:
        reason = str(failure)
        if not re.fullmatch('[A-Za-z0-9_]{1,96}', reason):
            reason = 'PARSE'
        raise ReaderError('R8_OPERATION1_' + reason.upper()) from None
    need(type(parsed) is dict and type(parsed.get('groups')) is list and (type(parsed.get('pads')) is list) and (type(parsed.get('branches')) is list) and bool(parsed['branches']), 'R8_OPERATION1_PARSED_SHAPE')
    need(type(expected_grid_dimensions) is tuple and len(expected_grid_dimensions) == 2, 'R8_GRID_CONTEXT')
    grid_width, grid_height = expected_grid_dimensions
    for branch in parsed['branches']:
        snapshot = branch.get('snapshot') if type(branch) is dict else None
        need(type(snapshot) is dict and snapshot.get('gridWidth') == grid_width and (snapshot.get('gridHeight') == grid_height), 'R8_GRID_BINDING')
    owner_group_count = len(parsed['groups'])
    need(is_int(owner_group_count, 1, MAX_GROUPS), 'R8_OWNER_GROUP_CENSUS')
    need(original_capture_groups >= owner_group_count, 'R8_CAPTURE_OWNER_GROUPS')
    need(groups_before_export - owner_group_count == wrapper_groups, 'R8_WRAPPER_GROUP_RECONCILIATION')
    prior_pad_projection = _r8_pad_projection(qualified_pads, 'R8_PRIOR_PAD')
    fresh_pad_projection = _r8_pad_projection(parsed['pads'], 'R8_FRESH_PAD')
    need(bool(prior_pad_projection) and fresh_pad_projection == prior_pad_projection, 'R8_PAD_PHYSICAL_IDENTITY_MISMATCH')
    return {'schema': 'R8_ORIGINAL_GROUP_CENSUS_DERIVED_ONLY', 'ownerGroupCount': owner_group_count, 'originalCaptureGroups': original_capture_groups, 'groupsBeforeExport': groups_before_export, 'wrapperGroupsBeforeExport': wrapper_groups, 'padBinding': 'QUALIFIED_PRIOR_PHYSICAL_IDENTITY_MATCH', 'padCount': len(prior_pad_projection), 'timingBinding': 'CONSERVATIVE_TOTAL_PREFIX_ENVELOPE', 'scope': 'AUTHENTICATED_OPERATION1_GROUP_AND_PAD_CENSUS_ONLY'}

def _freeze(value):
    if type(value) is dict:
        return MappingProxyType({key: _freeze(child) for key, child in value.items()})
    if type(value) is list:
        return tuple((_freeze(child) for child in value))
    if type(value) is tuple:
        return tuple((_freeze(child) for child in value))
    return value

def validate_full_d_v2(*, context, runtime_identity, op1_raw, result_raw, marker_raw):
    """Validate one explicit in-memory current-V2 packet.

    The result is path-free, immutable, and states semantic/derived evidence
    only. It does not establish native Meter, process, lifecycle, or publication
    acceptance.
    """
    need(type(context) is dict, 'CONTEXT_TYPE')
    need(type(marker_raw) is bytes and 2 <= len(marker_raw) <= MAX_MARKER_BYTES and (len(marker_raw) <= MAX_MARKER_TRANSPORT_BYTES), 'MARKER_SIZE_BEFORE_PARSE')
    try:
        _validate_context(context)
    except _ContextCodecError as failure:
        raise ReaderError(failure.code) from None
    except RecursionError:
        raise ReaderError('CONTEXT_DEPTH') from None
    _validate_context_order(context)
    _validate_runtime_identity(runtime_identity, context)
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= MAX_OPERATION1_BYTES, 'ORIGINAL_RECEIPT_BYTES')
    doc = _strict_json_line_core(result_raw, 'RESULT_JSON')
    marker = _strict_json_line_core(marker_raw, 'MARKER_JSON')
    result_bytes = _canonical_json_line(doc, result_raw, MAX_JSON_LINE, 'RESULT')
    marker_bytes = validate_marker_line(marker, marker_raw)
    semantics = _validate_result_semantics(doc, context, runtime_identity, op1_raw, result_raw)
    _validate_marker_binding(marker, marker_raw, result_raw, doc, runtime_identity)
    group_reconciliation = derive_original_group_reconciliation(doc, op1_raw, context['physicalContext']['qualifiedP07Pads'], _prior_grid_dimensions(context))
    export_reconciliation = derive_export_copy_reconciliation(doc, result_raw, marker, marker_raw)
    need(result_bytes == len(result_raw) and marker_bytes == len(marker_raw), 'OUTPUT_LENGTH_BINDING')
    arms = semantics['arms']
    summary = {'status': 'SEMANTIC_PACKET_VALIDATED_DERIVED_ONLY', 'runId': runtime_identity['runId'], 'sourceManifestSha256': runtime_identity['sourceManifestSha256'], 'wireContractSha256': runtime_identity['wireContractSha256'], 'inputContextSha256': runtime_identity['inputContextSha256'], 'resultSha256': sha(result_raw), 'markerSha256': sha(marker_raw), 'resultBytes': result_bytes, 'markerBytes': marker_bytes, 'qualifiedPrior': doc['qualifiedPrior'], 'arms': {name: {'disposition': arms[name]['facts']['outcome']['disposition'], 'reason': arms[name]['facts']['outcome']['reason'], 'expanded': arms[name]['facts']['outcome']['expanded'], 'traversed': len(arms[name]['freshRun']['ordering']['traversedNetIds']), 'observation': arms[name]['observationFacts']['state']} for name in ('null', 'observe')}, 'work': semantics['work'], 'originalGroupReconciliation': group_reconciliation, 'exportCopyReconciliation': export_reconciliation, 'evidenceScope': 'PURE_SEMANTIC_AND_DERIVED_DEMAND_ONLY'}
    return _freeze(summary)
__all__ = ('ReaderError', 'derive_export_copy_reconciliation', 'derive_original_group_reconciliation', 'validate_full_d_v2', 'validate_marker_line')
