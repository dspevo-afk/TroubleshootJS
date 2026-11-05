"""Strict, read-only independent branch-choice evidence consumer.

Fixture acceptance certifies this reader only. Native reports still require root
review of the actual compiler, process, context and cleanup evidence.
"""
from __future__ import annotations
import argparse
import base64
from collections import Counter, deque
import hashlib
import heapq
import json
import os
from pathlib import Path
import re
import stat

SCHEMA = 'p07-actual-branch-order-study-v6'
KINDS = ('READER_FIXTURE', 'TINY_NATIVE', 'AUTHORED')
ARMS = (
    ('PLAIN_ENTRY_RECEIPT_CONTROL', 'PLAIN_BEGIN', 'CANONICAL_NEAREST', 'CANONICAL', False, False),
    ('OBSERVED_DEFAULT_NEAREST', 'BOUNDARY_CONVENIENCE', 'CANONICAL_NEAREST', 'CANONICAL', True, False),
    ('OBSERVED_EXPLICIT_NEAREST', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST', 'CANONICAL', True, False),
    ('SECOND_ORDER_MINIMUM_PAIR', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST',
     'SECOND_ORDER_MINIMUM_PAIR', True, False),
    ('MINIMUM_PAIR_PUBLISHER_CAPTURE', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST',
     'SECOND_ORDER_MINIMUM_PAIR', True, True),
)
LIMITS = dict(operationNanos=90_000_000_000, groups=640, groupNanos=5_000_000_000,
              p07Steps=32768, receiptBytes=8*1024*1024, lineBytes=65536,
              padRows=1024, prefixBytes=1024*1024, viaCost=60, holeCap=48,
              transitionCap=4, orderings=3, requestExpansions=1_000_000,
              branchExpansions=100_000, cells=250_000, pathNodes=4096,
              snapshotEntries=1048576, publisherPayloadBytes=2*1024*1024,
              publisherChunkBytes=4096)
SNAP_INTS = ('pass startX startY rootX rootY gridWidth gridHeight goalsTop goalsBottom '
             'prefixViaCount reachedPadCount initialManhattanDistance '
             'minimumExploredManhattanDistance branchExpanded totalExpanded '
             'expandedTop expandedBottom frontierCount bestStateCount '
             'relaxedBoundWorkUnits relaxedBoundOrdinaryProbes relaxedBoundViaProbes '
             'relaxedBoundRefreshes').split()
SNAP_BOOLS = ('relaxedBoundStarted relaxedBoundComplete relaxedBoundObservationChecked '
              'relaxedBoundLiveLayoutStable relaxedBoundRouterStateStable '
              'relaxedBoundRouterEffectsStable').split()
SNAP_STRINGS = ['netId', 'startPadId', 'rootPadId', 'terminalReason']
RECEIPT_FIELDS = ('attempt startedOrderings completedOrderings yieldedOrderings '
                  'finalizedOrderings selectedOrdering expansions guidanceStarted '
                  'comparisonComplete termination').split()
GROUP_FIELDS = ('record operation ordinal phase elapsedBeforeOwnLineNanos '
                'generatorAdvances p07Slices p07Steps diagnosticCloneSteps accounting observerChecks '
                'callbacks censusRows netOrderRows hashInputBytes serializedBeforeOwnLineBytes '
                'publisherChunkRows publisherResultRows diagnosticCopiedEntries '
                'graphChunkRows graphResultRows graphCursorSteps '
                'diagnosticSerializedBytes diagnosticHashBytes diagnosticUnits').split()
NET_ORDER_FIELDS = ('record operation group pass strategy promotedNet completedOrderings '
                    'yieldedOrderings prefixSha256 prefixBytes netIds').split()
PUB_CHUNK_FIELDS = ('record operation group targetId index offset bytes data').split()
PUB_RESULT_FIELDS = ('record operation group targetId payloadBytes payloadSha256 chunkCount').split()
GRAPH_CHUNK_FIELDS = ('record operation group targetId index offset bytes data').split()
GRAPH_RESULT_FIELDS = ('record operation group targetId payloadBytes payloadSha256 chunkCount').split()
PUBLISHER_TARGET_SCHEMA = 'p07-publisher-target-v1'
PUBLISHER_TARGET_ID = 'op4/pass1/target0'
GRAPH_TARGET_SCHEMA = 'p07-outf-rejected-publisher-graph-v1'
GRAPH_TARGET_ID = 'op4/pass1/target0'
GRAPH_BODY_SCHEMA = 'p07-zero-via-graph-r5'
DIAGNOSTIC_CLONE_FIELDS = ('record operation ordinal group pass netId startPadId rootPadId '
                           'faceIndex prefixViaCount ownerSteps').split()
DIAGNOSTIC_BOUND_PROOFS = ('relaxedBoundObservationChecked relaxedBoundLiveLayoutStable '
                            'relaxedBoundRouterStateStable relaxedBoundRouterEffectsStable').split()
BASE_SHA = '6478608ca73d2a62791cd04d69ce697f8285b64b8925933a81c2c50ef5b78100'
BASE_ROWS = 1080
BASE_MANIFEST_BYTES = 208338
BASE_SOURCE_BYTES = 87552927
TOKEN = re.compile(r'[-+A-Za-z0-9_.:]{1,128}\Z')
HEX = re.compile(r'[0-9a-f]{64}\Z')


def need(condition, code):
    if not condition:
        raise ValueError(code)


def keys(value, names, code):
    need(type(value) is dict and set(value) == set(names), code)


def same_json_value(left, right):
    """Compare JSON values without Python's bool/int equality coercion."""
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return set(left) == set(right) and all(
            same_json_value(left[name], right[name]) for name in left)
    if type(left) is list:
        return len(left) == len(right) and all(
            same_json_value(a, b) for a, b in zip(left, right))
    return left == right


def integer(value, lo=0, hi=2**63-1):
    return type(value) is int and lo <= value <= hi


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def digest(value):
    return type(value) is str and HEX.fullmatch(value) is not None


def token(value):
    return type(value) is str and TOKEN.fullmatch(value) is not None


def strict_json(raw):
    need(type(raw) is bytes and not raw.startswith(b'\xef\xbb\xbf'), 'JSON_BOM')
    def pairs(rows):
        out = {}
        for k, v in rows:
            need(k not in out, 'JSON_DUPLICATE_KEY')
            out[k] = v
        return out
    def reject(_):
        raise ValueError('JSON_NONINTEGER')
    def number(text):
        need(text != '-0', 'JSON_NEGATIVE_ZERO')
        return int(text)
    value = json.loads(raw.decode('utf-8', errors='strict'), object_pairs_hook=pairs,
                       parse_int=number, parse_float=reject, parse_constant=reject)
    def strings(v):
        if type(v) is str:
            need(not any(0xD800 <= ord(c) <= 0xDFFF for c in v), 'JSON_SURROGATE')
        elif type(v) is dict:
            for k, item in v.items(): strings(k); strings(item)
        elif type(v) is list:
            for item in v: strings(item)
    strings(value)
    return value


def no_reparse(path):
    p = Path(os.path.abspath(path))
    for item in (p, *p.parents):
        st = item.lstat()
        need(not stat.S_ISLNK(st.st_mode) and not getattr(st, 'st_file_attributes', 0) & 0x400,
             'REPARSE_PATH')
    return p


def stable_read(path, maximum=8*1024*1024):
    p = no_reparse(path)
    before = p.stat()
    need(stat.S_ISREG(before.st_mode) and before.st_size <= maximum, 'FILE_SIZE_TYPE')
    def shared(st): return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink
    with p.open('rb') as stream:
        opened = os.fstat(stream.fileno()); raw = stream.read(maximum+1)
        closed = os.fstat(stream.fileno())
    after = p.stat()
    # Windows path stat and fstat expose different ctime values: preserve their
    # stability within each API while comparing shared identity across the APIs.
    need(shared(before) == shared(opened) == shared(closed) == shared(after)
         and before.st_ctime_ns == after.st_ctime_ns
         and opened.st_ctime_ns == closed.st_ctime_ns and len(raw) == after.st_size,
         'FILE_CHANGED')
    no_reparse(p)
    return raw


def file_ref(ref, base, maximum=8*1024*1024, relative=False):
    keys(ref, ('path', 'bytes', 'sha256'), 'FILE_REF_SCHEMA')
    need(type(ref['path']) is str and integer(ref['bytes'], 0, maximum)
         and digest(ref['sha256']), 'FILE_REF_VALUE')
    if relative:
        need(re.fullmatch(r'op-[0-4]\.jsonl', ref['path']) is not None, 'RECEIPT_PATH')
    p = Path(ref['path'])
    p = p if p.is_absolute() else Path(base)/p
    raw = stable_read(p, maximum)
    need(len(raw) == ref['bytes'] and sha(raw) == ref['sha256'], 'FILE_PIN')
    return raw, p


def pad_bytes(pads):
    return ''.join('\t'.join(str(row[k]) for k in ('padId','netId','componentId','x','y','role'))+'\n'
                   for row in pads).encode('utf-8')


def audit_net_order(row, pads, branches, arm):
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
    minimum_pair.extend(net for net in net_ids if net not in pair_metrics)
    base_order = canonical if arm in (1, 2) else minimum_pair
    expected = list(base_order)
    if row['promotedNet'] is not None:
        need(row['promotedNet'] in expected, 'NET_ORDER_PROMOTION_CENSUS')
        expected.remove(row['promotedNet'])
        expected.insert(0, row['promotedNet'])
    need(row['netIds'] == expected, 'NET_ORDER_VECTOR')

    # Pass-one callback order may omit an unvisited tail, but every callback
    # net must remain an order-preserving subsequence of the captured vector.
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

    return dict(strategy=row['strategy'], canonicalNetIds=canonical,
                minimumPairNetIds=minimum_pair, expectedNetIds=expected,
                baseNetIds=base_order,
                minimumPairs=[dict(netId=net, distance=pair_metrics[net],
                                  witnessPadIds=list(witnesses[net]))
                              for net in sorted(pair_metrics)],
                singletonNetIds=[net for net in net_ids if net not in pair_metrics],
                observedNetIds=list(row['netIds']))


def validate_net_order_promotion(row, branches, pad_count, net_count):
    """Bind promotion to complete pass-zero branch observations."""
    pass_zero = [branch['snapshot'] for branch in branches if branch['snapshot']['pass'] == 0]
    need(pass_zero and row['completedOrderings'] == 1, 'NET_ORDER_PRIOR_COUNTERS')
    if row['yieldedOrderings'] == 1:
        need(row['promotedNet'] is None
             and len(pass_zero) == pad_count - net_count
             and all(snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero),
             'NET_ORDER_YIELDED_PROMOTION')
    else:
        last = pass_zero[-1]
        need(not all(snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero),
             'NET_ORDER_AMBIGUOUS_PROMOTION')
        need(all(snapshot['terminalReason'] == 'FOUND' for snapshot in pass_zero[:-1])
             and last['terminalReason'] in ('NO_PATH', 'BRANCH_SEARCH_LIMIT')
             and row['promotedNet'] == last['netId'], 'NET_ORDER_FAILED_PROMOTION')


def validate_receipt(r):
    keys(r, RECEIPT_FIELDS, 'RECEIPT_SCHEMA')
    for name in RECEIPT_FIELDS[:7]: need(integer(r[name], -1 if name == 'selectedOrdering' else 0), 'RECEIPT_NUMBER')
    need(integer(r['attempt'],0,5) and integer(r['startedOrderings'],1,3)
         and integer(r['completedOrderings'],1,r['startedOrderings'])
         and integer(r['yieldedOrderings'],0,r['completedOrderings'])
         and integer(r['finalizedOrderings'],0,r['yieldedOrderings'])
         and integer(r['selectedOrdering'],-1,r['startedOrderings']-1)
         and integer(r['expansions'],0,1_000_000), 'RECEIPT_BOUNDS')
    need(type(r['guidanceStarted']) is bool and type(r['comparisonComplete']) is bool
         and type(r['termination']) is str and 0 < len(r['termination'].encode()) <= 256,
         'RECEIPT_TERMINAL')
    need(r['selectedOrdering'] < 0 or r['finalizedOrderings'] > 0, 'RECEIPT_WINNER')
    if r['comparisonComplete']:
        need(r['startedOrderings'] == r['completedOrderings'] == 3
             and r['termination'] != 'TOTAL_SEARCH_LIMIT', 'RECEIPT_COMPARISON')
    if r['termination'] == 'ORDERINGS_COMPLETE':
        need(r['comparisonComplete'] and r['yieldedOrderings'] > 0, 'RECEIPT_COMPLETE')
    if r['termination'] == 'FIRST_SUCCESS_UNGUIDED':
        need(not r['guidanceStarted'] and not r['comparisonComplete'] and r['yieldedOrderings'] == 1,
             'RECEIPT_FIRST_SUCCESS')


def utf16_length(value):
    return len(value.encode('utf-16-le')) // 2


def java_field(value):
    if value is None:
        return '-1:'
    return str(utf16_length(value)) + ':' + value


def java_text(value):
    return 'null' if value is None else value


def java_lexical_key(value):
    raw = value.encode('utf-16-le')
    return tuple(raw[i] | raw[i + 1] << 8 for i in range(0, len(raw), 2))


def _geometry_id(value):
    return (type(value) is str and 0 < len(value) <= 4096
            and not any(ord(ch) < 32 or ord(ch) == 127
                        or 0xD800 <= ord(ch) <= 0xDFFF for ch in value))


def _point_coordinate(value):
    return integer(value, -100_000_000, 100_000_000)


def _freeze(value):
    if type(value) is list:
        return tuple(_freeze(item) for item in value)
    if type(value) is dict:
        return tuple(sorted((key, _freeze(item)) for key, item in value.items()))
    return value


def _geometry_rows(snapshot):
    keys(snapshot, ('staticPrefix', 'traces', 'holes', 'geometrySha256', 'geometryBytes'),
         'PUB_GEOMETRY_SCHEMA')
    prefix = snapshot['staticPrefix']
    need(type(prefix) is str and not any(0xD800 <= ord(ch) <= 0xDFFF for ch in prefix),
         'PUB_GEOMETRY_PREFIX')
    traces = snapshot['traces']
    holes = snapshot['holes']
    need(type(traces) is list and type(holes) is list and len(holes) <= LIMITS['holeCap'],
         'PUB_GEOMETRY_ROWS')
    trace_ids = set()
    for row in traces:
        need(type(row) is list and len(row) == 9, 'PUB_TRACE_ROW')
        source_id, net_id, start_id, end_id, layer, exposure, width, xs, ys = row
        need(source_id is None or _geometry_id(source_id), 'PUB_TRACE_ID')
        if source_id is not None:
            need(source_id not in trace_ids, 'PUB_TRACE_ID_DUPLICATE')
            trace_ids.add(source_id)
        need(token(net_id) and (start_id is None or token(start_id))
             and (end_id is None or token(end_id)), 'PUB_TRACE_ENDPOINT')
        need(integer(layer, 0, 1) and exposure in ('EXPOSED', 'COVERED')
             and integer(width, 9, 63) and width % 2 == 1, 'PUB_TRACE_RULES')
        need(type(xs) is list and type(ys) is list and 2 <= len(xs) == len(ys)
             and len(xs) <= LIMITS['pathNodes']
             and all(_point_coordinate(x) for x in xs)
             and all(_point_coordinate(y) for y in ys), 'PUB_TRACE_POINTS')
    hole_ids = set()
    for row in holes:
        need(type(row) is list and len(row) == 8, 'PUB_HOLE_ROW')
        hole_id, kind, net_id, x, y, drill, land, exposure = row
        need(_geometry_id(hole_id) and hole_id not in hole_ids, 'PUB_HOLE_ID')
        hole_ids.add(hole_id)
        need(kind in ('VIA', 'PLATED', 'NON_PLATED')
             and exposure in ('EXPOSED', 'COVERED')
             and _point_coordinate(x) and _point_coordinate(y)
             and integer(drill, 1, 100_000_000)
             and integer(land, 1, 100_000_000), 'PUB_HOLE_RULES')
        if kind == 'NON_PLATED':
            need(net_id is None and land == drill, 'PUB_NPTH_RULES')
        else:
            need(token(net_id) and land > drill, 'PUB_PLATED_HOLE_RULES')
    fingerprint = geometry_fingerprint(prefix, traces, holes)
    raw = fingerprint.encode('utf-8')
    need(digest(snapshot['geometrySha256']) and snapshot['geometrySha256'] == sha(raw)
         and integer(snapshot['geometryBytes'], 1, LIMITS['prefixBytes'])
         and snapshot['geometryBytes'] == len(raw), 'PUB_GEOMETRY_FINGERPRINT')
    return fingerprint, raw


def geometry_fingerprint(static_prefix, traces, holes):
    """Rebuild PcbBoardLayout's native trace/hole suffix using Java ordering."""
    def trace_key(row):
        source_id, net_id, start_id, end_id, layer, _exposure, width, _xs, _ys = row
        return (java_lexical_key(net_id), java_lexical_key(java_text(start_id)),
                java_lexical_key(java_text(end_id)), layer, width,
                java_lexical_key(java_text(source_id)))

    out = [static_prefix]
    for row in sorted(traces, key=trace_key):
        source_id, net_id, start_id, end_id, layer, exposure, width, xs, ys = row
        layer_name = 'TOP' if layer == 0 else 'BOTTOM'
        out.append('T:' + java_field(source_id) + layer_name + ':' + exposure + ':'
                   + net_id + ':' + java_text(start_id) + '-' + java_text(end_id) + '@')
        if width != 9:
            out.append('W' + str(width) + ':')
        out.extend(str(x) + ',' + str(y) + ';' for x, y in zip(xs, ys))
    for row in sorted(holes, key=lambda item: java_lexical_key(item[0])):
        hole_id, kind, net_id, x, y, drill, land, exposure = row
        out.append('H:' + java_field(hole_id) + kind + java_field(net_id)
                   + str(x) + ',' + str(y) + ',' + str(drill) + ',' + str(land)
                   + ',' + exposure)
    return ''.join(out)


def _compact_piece(nodes):
    compact = []
    for node in nodes:
        point = (node[2], node[3])
        if len(compact) >= 2:
            a, b = compact[-2], compact[-1]
            if (a[0] == b[0] == point[0]) or (a[1] == b[1] == point[1]):
                compact.pop()
        compact.append(point)
    return compact


def _prefix_goal_supported(target, pads_by_id, endpoint_ids, x, y, layer, net_id):
    for pad_id in endpoint_ids:
        if (pads_by_id[pad_id]['x'], pads_by_id[pad_id]['y']) == (x, y):
            return True
    for trace in target['before']['traces']:
        _source, trace_net, _start, _end, trace_layer, _exposure, _width, xs, ys = trace
        if trace_net != net_id or trace_layer != layer:
            continue
        for index in range(len(xs) - 1):
            x1, y1, x2, y2 = xs[index], ys[index], xs[index + 1], ys[index + 1]
            if x1 == x2 == x and min(y1, y2) <= y <= max(y1, y2) \
                    and (y - min(y1, y2)) % target['grid']['pitch'] == 0:
                return True
            if y1 == y2 == y and min(x1, x2) <= x <= max(x1, x2) \
                    and (x - min(x1, x2)) % target['grid']['pitch'] == 0:
                return True
    for hole in target['before']['holes']:
        _hole_id, kind, hole_net, hole_x, hole_y, _drill, _land, _exposure = hole
        if (kind in ('VIA', 'PLATED') and hole_net == net_id
                and (hole_x, hole_y) == (x, y)):
            return True
    return False


def _publisher_geometry_delta(target, pads_by_id, net_roles, branches):
    branch = target['branch']
    nodes = target['path']['nodes']
    grid = target['grid']
    terminal_id = branch['foundTerminalPadId']
    net_id = branch['netId']
    prior_in_pass = [row for row in branches
                     if row['snapshot']['pass'] == target['context']['pass']
                     and row['ordinal'] <= branch['ordinal']]
    need(prior_in_pass and prior_in_pass[-1]['ordinal'] == branch['ordinal'],
         'PUB_PASS_BRANCH_ORDINAL')
    branch_id = len(prior_in_pass)
    widths = {'HIGH_CURRENT': 13, 'SUPPLY': 11, 'RETURN': 11,
              'CONTROL': 9, 'SIGNAL': 9}
    need(net_id in net_roles and net_roles[net_id] in widths,
         'PUB_NET_ROLE_WIDTH')
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
            points = _compact_piece(piece_nodes)
            endpoint_id = None
            if last == len(nodes) - 1:
                if terminal_id is not None:
                    endpoint_id = terminal_id
                elif (nodes[last][2], nodes[last][3]) == (
                        pads_by_id[branch['rootPadId']]['x'],
                        pads_by_id[branch['rootPadId']]['y']):
                    endpoint_id = branch['rootPadId']
            expected_traces.append([
                'p07/{}/{}/{}'.format(net_id, branch_id, piece_number), net_id,
                branch['startPadId'] if first == 0 else None, endpoint_id,
                piece_nodes[0][1], 'EXPOSED', width,
                [point[0] for point in points], [point[1] for point in points],
            ])
            expected_segments += len(piece_nodes) - 1
            piece_number += 1
        if end < len(nodes):
            via_x, via_y = nodes[last][2], nodes[last][3]
            at_xy = [hole for hole in known_holes if hole[3:5] == [via_x, via_y]]
            if at_xy:
                need(all(hole[1] in ('VIA', 'PLATED') and hole[2] == net_id
                         for hole in at_xy), 'PUB_VIA_FOREIGN_OR_NPTH')
            else:
                via = ['p07-via/{}/{}/{}'.format(net_id, branch_id, piece_number),
                       'VIA', net_id, via_x, via_y, 2, 6, 'EXPOSED']
                expected_holes.append(via)
                known_holes.append(via)
        first = end
    need(len(before_holes) + len(expected_holes) <= LIMITS['holeCap']
         and len(before_holes) + len(expected_holes) <= grid['maximumVias'],
         'PUB_VIA_CAP')
    return expected_traces, expected_holes, expected_segments


def _validate_publisher_document(raw, chunk_rows, result_row, result_line, operation, source_sha,
                                 header, result, branches, net_order, groups, pads):
    document = strict_json(raw)
    keys(document, ('schema', 'context', 'objects', 'branch', 'grid', 'checkpoints',
                    'path', 'publisher', 'ordering', 'before', 'after', 'integrity',
                    'limitations'), 'PUB_TARGET_SCHEMA')
    need(document['schema'] == PUBLISHER_TARGET_SCHEMA, 'PUB_TARGET_SCHEMA')
    ctx = document['context']
    keys(ctx, ('sourceManifestSha256', 'operation', 'rootSeed', 'placementSeed',
               'routingSeed', 'attempt', 'request', 'pass', 'placementSha256'),
         'PUB_CONTEXT_SCHEMA')
    candidate = header['candidate']
    receipt_context = result['context']
    need(ctx['sourceManifestSha256'] == source_sha and integer(ctx['operation'], 4, 4)
         and ctx['operation'] == operation and integer(ctx['request'], 0, 0)
         and integer(ctx['pass'], 1, 1)
         and all(type(ctx[name]) is str and ctx[name] == candidate[candidate_name]
                 for name, candidate_name in (('rootSeed', 'rootSeed'),
                                                ('placementSeed', 'placementSeed'),
                                                ('routingSeed', 'routingSeed')))
         and integer(ctx['attempt'], 0, 5)
         and ctx['attempt'] == receipt_context['selectedAttempt']
         and ctx['request'] == receipt_context['requestIndex']
         and digest(ctx['placementSha256'])
         and ctx['placementSha256'] == receipt_context['pristineSha256'],
         'PUB_CONTEXT_JOIN')
    if header['mode'] == 'AUTHORED':
        need((ctx['rootSeed'], ctx['placementSeed'], ctx['routingSeed'], ctx['attempt'])
             == ('9007199254740993', '-6587958367974952701',
                 '8445301674744508279', 4), 'PUB_AUTHORED_CONTEXT')
    else:
        need(header['mode'] == 'TINY_CANARY'
             and (ctx['rootSeed'], ctx['placementSeed'], ctx['routingSeed'])
             == ('42', '5526715536693200536', '4493419427463069983'),
             'PUB_TINY_CONTEXT')

    objects = document['objects']
    object_names = ('generator', 'route', 'search', 'branch', 'layout', 'publisher',
                    'found', 'terminal')
    keys(objects, object_names, 'PUB_OBJECTS_SCHEMA')
    for name in object_names:
        if name == 'terminal':
            need(objects[name] is None or integer(objects[name], 1), 'PUB_OBJECT_TOKEN')
        else:
            need(integer(objects[name], 1), 'PUB_OBJECT_TOKEN')
    nonnull_objects = [objects[name] for name in object_names if objects[name] is not None]
    need(len(nonnull_objects) == len(set(nonnull_objects)), 'PUB_OBJECT_ALIAS')

    branch = document['branch']
    keys(branch, ('ordinal', 'netVectorPosition', 'netId', 'startPadId', 'rootPadId',
                  'reachedPadIds', 'foundTerminalPadId', 'branchRecordGroup'),
         'PUB_BRANCH_SCHEMA')
    need(integer(branch['ordinal']) and integer(branch['netVectorPosition'])
         and integer(branch['branchRecordGroup']), 'PUB_BRANCH_IDENTITY')
    candidates = [row for row in branches
                  if row['ordinal'] == branch['ordinal']
                  and row['snapshot']['pass'] == ctx['pass']
                  and row['snapshot']['terminalReason'] == 'FOUND']
    need(len(candidates) == 1, 'PUB_BRANCH_UNIQUE')
    branch_row = candidates[0]
    snapshot = branch_row['snapshot']
    need(branch['branchRecordGroup'] == branch_row['group']
         and branch['netId'] == snapshot['netId']
         and branch['startPadId'] == snapshot['startPadId']
         and branch['rootPadId'] == snapshot['rootPadId']
         and (branch['foundTerminalPadId'] is None
              or token(branch['foundTerminalPadId'])), 'PUB_BRANCH_JOIN')
    need(type(branch['reachedPadIds']) is list
         and all(token(pad_id) for pad_id in branch['reachedPadIds'])
         and branch['reachedPadIds'] == sorted(set(branch['reachedPadIds']))
         and len(branch['reachedPadIds']) == snapshot['reachedPadCount']
         and branch['rootPadId'] in branch['reachedPadIds']
         and branch['startPadId'] not in branch['reachedPadIds'], 'PUB_REACHED_SET')
    reached_from_callbacks = {branch['rootPadId']}
    reached_from_callbacks.update(
        row['snapshot']['startPadId'] for row in branches
        if row['snapshot']['pass'] == ctx['pass']
        and row['snapshot']['netId'] == branch['netId']
        and row['ordinal'] < branch['ordinal'])
    need(branch['reachedPadIds'] == sorted(reached_from_callbacks),
         'PUB_REACHED_CALLBACK_JOIN')
    need(net_order is not None and branch['netVectorPosition'] < len(net_order['netIds'])
         and net_order['netIds'][branch['netVectorPosition']] == branch['netId'],
         'PUB_NET_VECTOR_JOIN')
    if header['mode'] == 'AUTHORED':
        need((branch['netId'], branch['startPadId'], branch['rootPadId'],
              snapshot['terminalReason']) == ('OUT_F', 'RSH_F.1', 'CSTORE_F.2', 'FOUND'),
             'PUB_AUTHORED_BRANCH')

    pads_by_id = {row['padId']: row for row in pads}
    net_roles = {}
    for pad in pads:
        previous = net_roles.setdefault(pad['netId'], pad['role'])
        need(previous == pad['role'], 'PUB_NET_ROLE')
    need(branch['netId'] in net_roles and branch['startPadId'] in pads_by_id
         and branch['rootPadId'] in pads_by_id
         and pads_by_id[branch['startPadId']]['netId'] == branch['netId']
         and pads_by_id[branch['rootPadId']]['netId'] == branch['netId'], 'PUB_PAD_JOIN')
    for pad_id in branch['reachedPadIds']:
        need(pad_id in pads_by_id and pads_by_id[pad_id]['netId'] == branch['netId'],
             'PUB_REACHED_PAD')
    terminal_id = branch['foundTerminalPadId']
    need(terminal_id is None or (terminal_id in pads_by_id
         and pads_by_id[terminal_id]['netId'] == branch['netId']
         and terminal_id in branch['reachedPadIds']), 'PUB_TERMINAL_PAD')

    grid = document['grid']
    keys(grid, ('width', 'height', 'cells', 'originX', 'originY', 'pitch',
                'primaryLayer', 'maximumVias'), 'PUB_GRID_SCHEMA')
    need(integer(grid['width'], 1, LIMITS['cells'])
         and integer(grid['height'], 1, LIMITS['cells'])
         and integer(grid['cells'], 1, LIMITS['cells'])
         and grid['cells'] == grid['width'] * grid['height']
         and grid['cells'] <= LIMITS['cells']
         and (grid['width'], grid['height']) == (snapshot['gridWidth'], snapshot['gridHeight'])
         and _point_coordinate(grid['originX']) and _point_coordinate(grid['originY'])
         and integer(grid['pitch'], 10, 10) and integer(grid['primaryLayer'], 0, 1)
         and integer(grid['maximumVias'], 0, LIMITS['holeCap']), 'PUB_GRID_VALUES')
    for pad_id in (branch['startPadId'], branch['rootPadId']):
        pad = pads_by_id[pad_id]
        dx, dy = pad['x'] - grid['originX'], pad['y'] - grid['originY']
        need(dx >= 0 and dy >= 0 and dx % grid['pitch'] == 0 and dy % grid['pitch'] == 0
             and dx // grid['pitch'] < grid['width'] and dy // grid['pitch'] < grid['height'],
             'PUB_PAD_GRID')

    path = document['path']
    keys(path, ('encoding', 'nodes', 'nodeTokens', 'foundGoal'), 'PUB_PATH_SCHEMA')
    need(path['encoding'] == 'cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow'
         and type(path['nodes']) is list and 1 <= len(path['nodes']) <= LIMITS['pathNodes']
         and type(path['nodeTokens']) is list and len(path['nodeTokens']) == len(path['nodes'])
         and all(integer(value, 1) for value in path['nodeTokens'])
         and len(set(path['nodeTokens'])) == len(path['nodeTokens'])
         and path['nodeTokens'][-1] == objects['found']
         and not (set(path['nodeTokens']) - {objects['found']}) & set(nonnull_objects)
         and type(path['foundGoal']) is bool and path['foundGoal'] is True,
         'PUB_PATH_HEADER')
    nodes = path['nodes']
    for index, node in enumerate(nodes):
        need(type(node) is list and len(node) == 11
             and all(integer(value, -2**31, 2**31-1) for value in node), 'PUB_PATH_NODE')
        cell, layer, x, y, direction, transitions, cost, heuristic, serial, key_value, parent = node
        need(integer(cell, 0, grid['cells'] - 1) and integer(layer, 0, 1)
             and _point_coordinate(x) and _point_coordinate(y)
             and integer(direction, 0, 4) and integer(transitions, 0, LIMITS['transitionCap'])
             and integer(cost, 0, 2**31-1) and integer(heuristic, 0, 2**31-1)
             and integer(serial, 0, 2**31-1) and integer(key_value, 0, 2**31-1)
             and parent == (-1 if index == 0 else index - 1), 'PUB_PATH_NODE_DOMAIN')
        need((x, y) == (grid['originX'] + (cell % grid['width']) * grid['pitch'],
                        grid['originY'] + (cell // grid['width']) * grid['pitch']),
             'PUB_PATH_GRID')
        need(key_value == (((transitions * 2 + layer) * 5 + direction) * grid['cells']) + cell,
             'PUB_PATH_KEY')
        if index == 0:
            need(direction == 4 and transitions == 0, 'PUB_PATH_ROOT')
        else:
            previous = nodes[index - 1]
            same_layer = layer == previous[1]
            dx, dy = x - previous[2], y - previous[3]
            if same_layer:
                need(abs(dx) + abs(dy) == grid['pitch'] and direction in range(4)
                     and transitions == previous[5] and cost >= previous[6]
                     and serial > previous[8], 'PUB_PATH_STEP')
                expected_direction = {(0, -grid['pitch']): 0,
                                      (grid['pitch'], 0): 1,
                                      (0, grid['pitch']): 2,
                                      (-grid['pitch'], 0): 3}.get((dx, dy))
                need(direction == expected_direction, 'PUB_PATH_DIRECTION')
            else:
                need((dx, dy) == (0, 0) and direction == 4
                     and transitions == previous[5] + 1 and cost >= previous[6]
                     and serial > previous[8], 'PUB_PATH_VIA_STEP')
    first_node, found_node = nodes[0], nodes[-1]
    start_pad = pads_by_id[branch['startPadId']]
    need((first_node[2], first_node[3]) == (start_pad['x'], start_pad['y'])
         and (first_node[2], first_node[3]) == (snapshot['startX'], snapshot['startY'])
         and first_node[1] == grid['primaryLayer'],
         'PUB_PATH_START')
    endpoint_ids = set(branch['reachedPadIds']) | {branch['rootPadId']}
    if terminal_id is not None:
        need((pads_by_id[terminal_id]['x'], pads_by_id[terminal_id]['y'])
             == (found_node[2], found_node[3]), 'PUB_PATH_TERMINAL_ENDPOINT')
    else:
        need(_prefix_goal_supported(document, pads_by_id, endpoint_ids,
                                    found_node[2], found_node[3], found_node[1],
                                    branch['netId']),
             'PUB_PATH_GOAL_ENDPOINT')

    need((objects['terminal'] is None) == (terminal_id is None),
         'PUB_TERMINAL_OBJECT_JOIN')

    checkpoints = document['checkpoints']
    checkpoint_names = ('found', 'publisherSeen', 'publisherTerminal', 'orderingReady',
                        'orderingResult')
    keys(checkpoints, checkpoint_names, 'PUB_CHECKPOINT_SCHEMA')
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
        keys(value, ('group', 'sequence'), 'PUB_CHECKPOINT_VALUE')
        need(integer(value['group'], 0, len(groups) - 1)
             and integer(value['sequence'], 1, 2**31-1), 'PUB_CHECKPOINT_VALUE')
        group = groups[value['group']]
        need(group['phase'] == 'ADVANCE' and group['p07Slices'] == 1
             and group['observerChecks'] >= 1
             and value['sequence'] <= cumulative_observers[value['group']],
             'PUB_CHECKPOINT_GROUP')
        checkpoint_values.append(value)
    populated = [value for value in checkpoint_values if value is not None]
    need(len(populated) >= 1 and all(a['sequence'] <= b['sequence']
         and a['group'] <= b['group'] for a, b in zip(populated, populated[1:])),
         'PUB_CHECKPOINT_ORDER')
    for left, right in zip(populated, populated[1:]):
        if left['sequence'] == right['sequence']:
            need(left['group'] == right['group'], 'PUB_CHECKPOINT_COOBSERVATION')
        else:
            need(left['sequence'] < right['sequence'], 'PUB_CHECKPOINT_POLL_SEQUENCE')
    by_group = {}
    for value in populated:
        by_group.setdefault(value['group'], set()).add(value['sequence'])
    need(all(len(sequences) <= observers_by_group[group] for group, sequences in by_group.items()),
         'PUB_CHECKPOINT_OBSERVER_COUNT')
    found_checkpoint = checkpoints['found']
    need(found_checkpoint is not None and found_checkpoint['group'] == branch_row['group'],
         'PUB_FOUND_CHECKPOINT')
    need(checkpoints['publisherSeen'] is not None
         and checkpoints['publisherTerminal'] is not None
         and checkpoints['orderingResult'] is not None, 'PUB_REQUIRED_CHECKPOINT')

    before_fingerprint, before_raw = _geometry_rows(document['before'])
    after_fingerprint, after_raw = _geometry_rows(document['after'])
    # The source snapshot's historical name counts BranchSearch.holes.length,
    # including every declared hole kind, as does the whole-layout gate.
    need(len(document['before']['holes']) == snapshot['prefixViaCount'],
         'PUB_PREFIX_VIA_COUNT')
    need(document['before']['staticPrefix'] == document['after']['staticPrefix'],
         'PUB_STATIC_PREFIX_CHANGED')
    need(document['before']['geometrySha256'] == branch_row['prefixSha256']
         and document['before']['geometryBytes'] == branch_row['prefixBytes'],
         'PUB_BRANCH_PREFIX_JOIN')
    need(branch_row['prefixSha256'] == sha(before_raw)
         and branch_row['prefixBytes'] == len(before_raw), 'PUB_BRANCH_PREFIX_RAW')
    before_traces = Counter(_freeze(row) for row in document['before']['traces'])
    after_traces = Counter(_freeze(row) for row in document['after']['traces'])
    before_holes = Counter(_freeze(row) for row in document['before']['holes'])
    after_holes = Counter(_freeze(row) for row in document['after']['holes'])
    need(before_traces <= after_traces and before_holes <= after_holes,
         'PUB_PREFIX_ROWS_NOT_CONSERVED')

    publisher = document['publisher']
    keys(publisher, ('outcome', 'stage', 'qualityIndex', 'reversedSize', 'storedLength',
                     'storedBends', 'storedLastDirection', 'segments', 'reason',
                     'sourceHandoff', 'startReachedAfter'), 'PUB_PUBLISHER_SCHEMA')
    need(publisher['outcome'] in ('PUBLISHED', 'REJECTED'), 'PUB_CAPTURE_INCOMPLETE')
    need(integer(publisher['stage'], 0, 6)
         and integer(publisher['qualityIndex'], 0, LIMITS['pathNodes'])
         and integer(publisher['reversedSize'], 1, LIMITS['pathNodes'])
         and integer(publisher['storedLength'], 0, LIMITS['pathNodes'] * 10)
         and integer(publisher['storedBends'], 0, LIMITS['pathNodes'] - 1)
         and integer(publisher['storedLastDirection'], 0, 4)
         and integer(publisher['segments'], 0, LIMITS['pathNodes'] - 1)
         and type(publisher['sourceHandoff']) is bool
         and type(publisher['startReachedAfter']) is bool
         and (publisher['reason'] is None or
              (type(publisher['reason']) is str
               and 0 < len(publisher['reason'].encode('utf-8')) <= 256)),
         'PUB_PUBLISHER_VALUES')
    need(publisher['reversedSize'] == len(nodes)
         and publisher['qualityIndex'] == len(nodes), 'PUB_PUBLISHER_PATH_SIZE')
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
    need(transitions == found_node[5] and transitions <= LIMITS['transitionCap'],
         'PUB_PATH_TRANSITIONS')
    direct = abs(start_pad['x'] - found_node[2]) + abs(start_pad['y'] - found_node[3])
    predicates = (direct == 0, path_length > direct * 3, bends > 16)
    need((publisher['storedLength'], publisher['storedBends'], publisher['storedLastDirection'])
         == (path_length, bends, last_direction), 'PUB_STORED_GUARD')
    expected_traces, expected_holes, expected_segments = _publisher_geometry_delta(
        document, pads_by_id, net_roles, branches)

    ordering = document['ordering']
    keys(ordering, ('routerStatus', 'routerReason', 'readyLayoutToken', 'readyPass',
                    'finalizerStatus', 'finalizedBefore', 'finalizedAfter',
                    'lastFailureDetail'), 'PUB_ORDERING_SCHEMA')
    need(ordering['routerStatus'] in ('READY', 'ROUTER_REJECTED')
         and type(ordering['routerReason']) is str
         and 0 < len(ordering['routerReason'].encode('utf-8')) <= 256
         and ordering['finalizerStatus'] in ('NOT_REACHED', 'FINALIZED', 'REJECTED'),
         'PUB_ORDERING_VALUES')
    need(ordering['lastFailureDetail'] is None or
         (type(ordering['lastFailureDetail']) is str
          and len(ordering['lastFailureDetail'].encode('utf-8')) <= 4096),
         'PUB_FINALIZER_DETAIL')
    if ordering['routerStatus'] == 'READY':
        need(integer(ordering['readyLayoutToken'], 1)
             and ordering['readyLayoutToken'] == objects['layout']
             and integer(ordering['readyPass'], 1, 1)
             and checkpoints['orderingReady'] is not None
             and checkpoints['orderingResult'] is not None
             and checkpoints['orderingReady']['sequence']
                 < checkpoints['orderingResult']['sequence'], 'PUB_READY_JOIN')
        need(ordering['finalizerStatus'] in ('FINALIZED', 'REJECTED')
             and integer(ordering['finalizedBefore'], 0, 3)
             and integer(ordering['finalizedAfter'], 0, 3), 'PUB_FINALIZER_JOIN')
        if ordering['finalizerStatus'] == 'FINALIZED':
            need(ordering['finalizedAfter'] == ordering['finalizedBefore'] + 1,
                 'PUB_FINALIZED_COUNTER')
        else:
            need(ordering['finalizedAfter'] == ordering['finalizedBefore']
                 and type(ordering['lastFailureDetail']) is str
                 and len(ordering['lastFailureDetail'].encode('utf-8')) > 0,
                 'PUB_REJECTED_COUNTER')
    else:
        need(ordering['readyLayoutToken'] is None and ordering['readyPass'] is None
             and checkpoints['orderingReady'] is None
             and ordering['finalizerStatus'] == 'NOT_REACHED'
             and integer(ordering['finalizedBefore'], 0, 3)
             and integer(ordering['finalizedAfter'], 0, 3)
             and ordering['finalizedBefore'] == ordering['finalizedAfter'],
             'PUB_ROUTER_REJECTED_JOIN')
    need(ordering['finalizedAfter'] <= result['receipt']['finalizedOrderings'],
         'PUB_REQUEST_RECEIPT_JOIN')
    if ordering['finalizerStatus'] == 'FINALIZED':
        need(result['receipt']['selectedOrdering'] >= 0
             and result['route'] == 'REQUEST_FINALIZED'
             and result['winner'] is not None, 'PUB_FINALIZED_REQUEST')

    if publisher['outcome'] == 'PUBLISHED':
        need(not any(predicates) and publisher['stage'] == 6
             and publisher['reason'] is None
             and publisher['sourceHandoff'] is True
             and publisher['startReachedAfter'] is True
             and publisher['segments'] == expected_segments, 'PUB_PUBLISHED_BOUNDARY')
        need(after_traces == before_traces + Counter(_freeze(row) for row in expected_traces)
             and after_holes == before_holes + Counter(_freeze(row) for row in expected_holes),
             'PUB_GEOMETRY_DELTA')
    else:
        expected_reason = branch['netId'] + ':BRANCH_QUALITY_LIMIT'
        need(any(predicates) and publisher['stage'] == 1
             and publisher['reason'] == expected_reason
             and publisher['sourceHandoff'] is False
             and publisher['startReachedAfter'] is False
             and publisher['segments'] == 0
             and ordering['routerStatus'] == 'ROUTER_REJECTED'
             and ordering['routerReason'] == expected_reason
             and before_fingerprint == after_fingerprint
             and document['before'] == document['after'], 'PUB_REJECTED_BOUNDARY')
    integrity = document['integrity']
    integrity_names = ('ownerBindingVerified', 'immutablePathVerified',
                       'staticGeometryEqual', 'prefixRowsConserved',
                       'sourceReferencesReleased')
    keys(integrity, integrity_names, 'PUB_INTEGRITY_SCHEMA')
    need(all(type(integrity[name]) is bool and integrity[name] is True
             for name in integrity_names), 'PUB_INTEGRITY')
    limitations = document['limitations']
    keys(limitations, ('routeAdoption', 'layoutQualification', 'playable100',
                       'fullPhysicalProof'), 'PUB_LIMITATIONS_SCHEMA')
    need(type(limitations['routeAdoption']) is bool
         and limitations['routeAdoption'] is False
         and type(limitations['layoutQualification']) is bool
         and limitations['layoutQualification'] is False
         and type(limitations['playable100']) is int
         and limitations['playable100'] == 0
         and type(limitations['fullPhysicalProof']) is str
         and limitations['fullPhysicalProof'] == 'NOT_RUN', 'PUB_LIMITATIONS')

    need(result_row['targetId'] == PUBLISHER_TARGET_ID
         and result_row['payloadBytes'] == len(raw)
         and result_row['chunkCount'] == len(chunk_rows)
         and digest(result_row['payloadSha256'])
         and result_row['payloadSha256'] == sha(raw), 'PUB_RESULT_SEAL')
    return dict(document=document, payloadBytes=len(raw), payloadSha256=sha(raw),
                captureHashBytes=(len(raw) + document['before']['geometryBytes']
                                  + document['after']['geometryBytes']
                                  + sum(len(line) for _row, line in chunk_rows)
                                  + len(result_line)),
                geometryBytes=(document['before']['geometryBytes']
                               + document['after']['geometryBytes']))


def _json_space(raw, position):
    while position < len(raw) and raw[position] in b' \t\r\n':
        position += 1
    return position


def _json_string_end(raw, position):
    need(position < len(raw) and raw[position] == 34, 'GRAPH_BODY_EXTRACT')
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


def _json_value_end(raw, position):
    position = _json_space(raw, position)
    need(position < len(raw), 'GRAPH_BODY_EXTRACT')
    if raw[position] == 34:
        return _json_string_end(raw, position)
    if raw[position] not in (91, 123):
        end = position
        while end < len(raw) and raw[end] not in b',]} \t\r\n':
            end += 1
        need(end > position, 'GRAPH_BODY_EXTRACT')
        return end
    stack = [125 if raw[position] == 123 else 93]
    position += 1
    while position < len(raw) and stack:
        value = raw[position]
        if value == 34:
            position = _json_string_end(raw, position)
            continue
        if value == 123:
            stack.append(125)
        elif value == 91:
            stack.append(93)
        elif value in (125, 93):
            need(value == stack[-1], 'GRAPH_BODY_EXTRACT')
            stack.pop()
            if not stack:
                return position + 1
        position += 1
    raise ValueError('GRAPH_BODY_EXTRACT')


def _top_level_value_bytes(raw, wanted):
    """Return one top-level JSON value's exact token bytes, excluding whitespace."""
    position = _json_space(raw, 0)
    need(position < len(raw) and raw[position] == 123, 'GRAPH_BODY_EXTRACT')
    position += 1
    while True:
        position = _json_space(raw, position)
        need(position < len(raw), 'GRAPH_BODY_EXTRACT')
        if raw[position] == 125:
            break
        key_end = _json_string_end(raw, position)
        key = strict_json(raw[position:key_end])
        need(type(key) is str, 'GRAPH_BODY_EXTRACT')
        position = _json_space(raw, key_end)
        need(position < len(raw) and raw[position] == 58, 'GRAPH_BODY_EXTRACT')
        value_start = _json_space(raw, position + 1)
        value_end = _json_value_end(raw, value_start)
        if key == wanted:
            return raw[value_start:value_end]
        position = _json_space(raw, value_end)
        need(position < len(raw), 'GRAPH_BODY_EXTRACT')
        if raw[position] == 44:
            position += 1
            continue
        need(raw[position] == 125, 'GRAPH_BODY_EXTRACT')
        break
    raise ValueError('GRAPH_BODY_MISSING')


def _canonical_graph_body(body):
    stability_names = ('prefixGeometrySha256', 'liveBeforeVerified', 'liveAfterVerified',
                       'liveEffectsUnchanged', 'goalVerified', 'originalPathVerified')
    grid_names = ('width', 'height', 'cells', 'originX', 'originY', 'pitch', 'startCell',
                  'initialLayerMask', 'endCell', 'endLayer', 'goalUnionCellCount')
    path_names = ('nodeCount', 'planarEdgeCount', 'transitionCount', 'tupleSha256')
    work_names = ('advanceCalls', 'workSteps', 'copyEntries', 'comparisonEntries',
                  'geometryBytes', 'effectUnits', 'ordinaryQueries', 'goalCells',
                  'reachedPadChecks', 'encodedCells', 'pathNodes', 'pathEdgesVerified',
                  'tupleBytes', 'serializedUtf16Units', 'activeMillis')
    masks_names = ('topStepsHex', 'bottomStepsHex', 'goalFacesHex')

    def quoted(value):
        need(type(value) is str and value.isascii(), 'GRAPH_BODY_ASCII')
        return json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode('ascii')

    def scalar(value):
        if type(value) is str:
            return quoted(value)
        if type(value) is bool:
            return b'true' if value else b'false'
        need(type(value) is int, 'GRAPH_BODY_SCALAR')
        return str(value).encode('ascii')

    def obj(fields):
        return b'{' + b','.join(quoted(name) + b':' + value for name, value in fields) + b'}'

    stability = obj((name, scalar(body['stability'][name]))
                    for name in stability_names)
    grid = obj((name, scalar(body['grid'][name])) for name in grid_names)
    path = obj((name, scalar(body['path'][name])) for name in path_names)
    work = obj((name, str(body['work'][name]).rjust(19, ' ').encode('ascii'))
               for name in work_names)
    masks = obj((name, scalar(body['masks'][name])) for name in masks_names)
    return obj((('schema', scalar(body['schema'])), ('stability', stability),
                ('grid', grid), ('path', path), ('work', work), ('masks', masks)))


def _graph_distances(document):
    """Compute directed ordinary-edge distances independently by BFS and Dijkstra."""
    body = document['body']
    grid = body['grid']
    masks = body['masks']
    width, height, cells = grid['width'], grid['height'], grid['cells']
    start = grid['startCell']
    top, bottom = masks['topStepsHex'], masks['bottomStepsHex']
    bfs_results = []
    for layer, mask in enumerate((top, bottom)):
        distance = [None] * cells
        parent = [None] * cells
        distance[start] = 0
        queue = deque([start])
        while queue:
            cell = queue.popleft()
            x, y = cell % width, cell // width
            flags = int(mask[cell], 16)
            # Stable witness order: north, east, south, west.
            neighbors = ((1, cell - width, y > 0),
                         (2, cell + 1, x + 1 < width),
                         (4, cell + width, y + 1 < height),
                         (8, cell - 1, x > 0))
            for bit, neighbor, inside in neighbors:
                if inside and flags & bit and distance[neighbor] is None:
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
            physical, cell = heapq.heappop(pending)
            if distance[cell] != physical:
                continue
            x, y = cell % width, cell // width
            flags = int(mask[cell], 16)
            # Independent coordinate and bit enumeration for the heap oracle.
            candidates = ((8, x - 1, y, x > 0),
                          (4, x, y + 1, y + 1 < height),
                          (2, x + 1, y, x + 1 < width),
                          (1, x, y - 1, y > 0))
            for bit, nx, ny, inside in candidates:
                if not inside or not flags & bit:
                    continue
                neighbor = ny * width + nx
                candidate = physical + grid['pitch']
                if distance[neighbor] is None or candidate < distance[neighbor]:
                    distance[neighbor] = candidate
                    heapq.heappush(pending, (candidate, neighbor))
        dijkstra_results.append(distance)

    return [dict(layer=layer, bfsEdges=bfs_results[layer][0],
                 dijkstraLength=dijkstra_results[layer],
                 bfsParent=bfs_results[layer][1]) for layer in range(2)]


def analyze_graph(document):
    """Return bounded per-seed-face ordinary-edge length diagnostics."""
    body = document['body']
    grid = body['grid']
    masks = body['masks']
    width, height, cells, pitch = grid['width'], grid['height'], grid['cells'], grid['pitch']
    start = grid['startCell']
    initial_mask = document['source']['initialLayerMask']
    sx = grid['originX'] + (start % width) * pitch
    sy = grid['originY'] + (start // width) * pitch
    found_cell, found_layer = grid['endCell'], grid['endLayer']
    distances = _graph_distances(document)
    faces = []

    def make_witness(layer, cell, owner):
        bfs, dijkstra, parent = owner['bfsEdges'], owner['dijkstraLength'], owner['bfsParent']
        reverse = [cell]
        current = cell
        while current != start:
            current = parent[current]
            need(current is not None and len(reverse) <= cells, 'GRAPH_BFS_PARENT')
            reverse.append(current)
        route = list(reversed(reverse))
        mask = masks['topStepsHex' if layer == 0 else 'bottomStepsHex']
        for left, right in zip(route, route[1:]):
            lx, ly = left % width, left // width
            rx, ry = right % width, right // width
            bit = (1 if rx == lx and ry == ly - 1 else
                   2 if rx == lx + 1 and ry == ly else
                   4 if rx == lx and ry == ly + 1 else
                   8 if rx == lx - 1 and ry == ly else 0)
            need(bit != 0 and int(mask[left], 16) & bit, 'GRAPH_WITNESS_EDGE')
        edges = bfs[cell]
        length = dijkstra[cell]
        need(edges is not None and length == edges * pitch, 'GRAPH_WITNESS_DISTANCE')
        direct = abs((cell % width) * pitch + grid['originX'] - sx) + abs(
            (cell // width) * pitch + grid['originY'] - sy)
        preimage = '\n'.join(f'{index},{layer},{node}'
                             for index, node in enumerate(route)).encode('ascii')
        return dict(cell=cell, x=grid['originX'] + (cell % width) * pitch,
                    y=grid['originY'] + (cell // width) * pitch, direct=direct,
                    edges=edges, length=length, pathNodeCount=len(route),
                    pathSha256=sha(preimage))

    for owner in distances:
        layer = owner['layer']
        if not initial_mask & (1 << layer):
            continue
        goal_characters = [ord(ch) - 48 for ch in masks['goalFacesHex']]
        goals = [cell for cell, value in enumerate(goal_characters)
                 if value & (1 << layer)]
        bfs, dijkstra = owner['bfsEdges'], owner['dijkstraLength']
        for cell in goals:
            expected = None if bfs[cell] is None else bfs[cell] * pitch
            need(dijkstra[cell] == expected, 'GRAPH_ORACLE_DISAGREEMENT')
        reachable = [cell for cell in goals if dijkstra[cell] is not None]
        eligible = [cell for cell in reachable
                    if (abs(grid['originX'] + (cell % width) * pitch - sx)
                        + abs(grid['originY'] + (cell // width) * pitch - sy)) > 0
                    and dijkstra[cell] <= 3 * (
                        abs(grid['originX'] + (cell % width) * pitch - sx)
                        + abs(grid['originY'] + (cell // width) * pitch - sy))]
        shortest = min(reachable, key=lambda cell: (dijkstra[cell], cell), default=None)
        shortest_eligible = min(eligible, key=lambda cell: (dijkstra[cell], cell), default=None)
        faces.append(dict(layer=layer, goalFaceCount=len(goals),
                          reachableGoalCount=len(reachable),
                          lengthEligibleGoalCount=len(eligible),
                          shortestReachable=(None if shortest is None else
                                             make_witness(layer, shortest, owner)),
                          shortestLengthEligible=(None if shortest_eligible is None else
                                                   make_witness(layer, shortest_eligible, owner)),
                          foundEndpointLength=(dijkstra[found_cell]
                              if found_layer == layer else None)))
    return faces


def validate_graph_document(raw, graph_chunks, result_row, result_line, publisher_capture,
                            header, result, entry, source_sha, branches, net_order, groups, pads):
    document = strict_json(raw)
    keys(document, ('schema', 'targetId', 'publisherPayloadSha256', 'context', 'objects',
                    'branch', 'source', 'goalFacesSha256', 'phaseWork', 'body', 'integrity',
                    'limitations'), 'GRAPH_DOCUMENT_SCHEMA')
    need(document['schema'] == GRAPH_TARGET_SCHEMA and document['targetId'] == GRAPH_TARGET_ID,
         'GRAPH_DOCUMENT_IDENTITY')
    need(type(graph_chunks) is list and graph_chunks, 'GRAPH_CHUNKS_MISSING')
    payload = b''
    for index, (row, line, decoded) in enumerate(graph_chunks):
        keys(row, GRAPH_CHUNK_FIELDS, 'GRAPH_CHUNK_SCHEMA')
        need(row['record'] == 'GRAPH_CHUNK' and integer(row['operation'], 4, 4)
             and row['targetId'] == GRAPH_TARGET_ID and integer(row['group'], 0, 639)
             and integer(row['index'], 0, LIMITS['publisherPayloadBytes'])
             and row['index'] == index and integer(row['offset'], 0, LIMITS['publisherPayloadBytes'])
             and row['offset'] == len(payload) and integer(row['bytes'], 1, LIMITS['publisherChunkBytes'])
             and type(row['data']) is str, 'GRAPH_CHUNK_IDENTITY')
        try:
            encoded = row['data'].encode('ascii')
            actual = base64.b64decode(encoded, validate=True)
        except (UnicodeEncodeError, ValueError):
            raise ValueError('GRAPH_CHUNK_BASE64')
        need(base64.b64encode(actual).decode('ascii') == row['data']
             and actual == decoded and len(actual) == row['bytes'], 'GRAPH_CHUNK_BASE64')
        payload += actual
        need(len(payload) <= LIMITS['publisherPayloadBytes'], 'GRAPH_PAYLOAD_LIMIT')
    keys(result_row, GRAPH_RESULT_FIELDS, 'GRAPH_RESULT_SCHEMA')
    need(type(result_line) is bytes and result_row['record'] == 'GRAPH_RESULT'
         and integer(result_row['operation'], 4, 4) and result_row['targetId'] == GRAPH_TARGET_ID
         and integer(result_row['group'], 0, 639)
         and integer(result_row['payloadBytes'], 1, LIMITS['publisherPayloadBytes'])
         and digest(result_row['payloadSha256'])
         and integer(result_row['chunkCount'], 1, LIMITS['publisherPayloadBytes'])
         and result_row['payloadBytes'] == len(payload)
         and result_row['chunkCount'] == len(graph_chunks)
         and result_row['payloadSha256'] == sha(payload) and payload == raw,
         'GRAPH_RESULT_SEAL')

    publisher = publisher_capture['document']
    need(document['publisherPayloadSha256'] == publisher_capture['payloadSha256'],
         'GRAPH_PUBLISHER_SEAL_JOIN')
    context = document['context']
    keys(context, ('sourceManifestSha256', 'operation', 'rootSeed', 'placementSeed',
                   'routingSeed', 'attempt', 'request', 'pass', 'placementSha256'),
         'GRAPH_CONTEXT_SCHEMA')
    need(same_json_value(context, publisher['context'])
         and context['sourceManifestSha256'] == source_sha,
         'GRAPH_CONTEXT_JOIN')
    need(type(result) is dict and result['winner'] is None
         and publisher['publisher']['outcome'] == 'REJECTED', 'GRAPH_REQUEST_DISPOSITION')

    objects = document['objects']
    object_names = ('generator', 'route', 'search', 'branch', 'layout', 'publisher',
                    'found', 'terminal')
    keys(objects, object_names, 'GRAPH_OBJECTS_SCHEMA')
    need(same_json_value(objects, publisher['objects']), 'GRAPH_OBJECTS_JOIN')
    branch = document['branch']
    branch_names = ('ordinal', 'netVectorPosition', 'netId', 'startPadId', 'rootPadId',
                    'reachedPadIds', 'foundTerminalPadId', 'branchRecordGroup')
    keys(branch, branch_names, 'GRAPH_BRANCH_SCHEMA')
    need(same_json_value(branch, publisher['branch']), 'GRAPH_BRANCH_JOIN')
    branch_matches = [row for row in branches if row['ordinal'] == branch['ordinal']
                      and row['snapshot']['pass'] == context['pass']
                      and row['snapshot']['terminalReason'] == 'FOUND']
    need(len(branch_matches) == 1 and net_order is not None, 'GRAPH_BRANCH_SOURCE_JOIN')
    branch_row = branch_matches[0]
    pads_by_id = {row['padId']: row for row in pads}
    need(branch['startPadId'] in pads_by_id and branch['rootPadId'] in pads_by_id,
         'GRAPH_ENDPOINT_PAD_JOIN')

    source = document['source']
    source_names = ('target', 'sourceKind', 'policy', 'initialLayerMask', 'activeTraceWidth',
                    'facesToken', 'routerTokens', 'goalArrayToken', 'reachedTerminalsArrayToken',
                    'startPadToken', 'rootPadToken', 'reachedPthPadIds')
    keys(source, source_names, 'GRAPH_SOURCE_SCHEMA')
    need(source['target'] == 'OUT_F'
         and source['sourceKind'] == 'OUT_F_REJECTED_PUBLISHER_PATH'
         and source['policy'] == 'FULLER_TWO_LAYER'
         and integer(source['initialLayerMask'], 3, 3)
         and integer(source['activeTraceWidth'], 1, 2**31-1), 'GRAPH_SOURCE_IDENTITY')
    roles = {row['netId']: row['role'] for row in pads}
    widths = {'HIGH_CURRENT': 13, 'SUPPLY': 11, 'RETURN': 11,
              'CONTROL': 9, 'SIGNAL': 9}
    need(branch['netId'] in roles and roles[branch['netId']] in widths
         and source['activeTraceWidth'] == widths[roles[branch['netId']]],
         'GRAPH_ACTIVE_ROLE_WIDTH')
    need(type(source['routerTokens']) is list and len(source['routerTokens']) == 2
         and all(integer(value, 1, 2**31-1) for value in source['routerTokens'])
         and type(source['reachedPthPadIds']) is list
         and all(token(value) for value in source['reachedPthPadIds'])
         and source['reachedPthPadIds'] == sorted(set(source['reachedPthPadIds'])),
         'GRAPH_SOURCE_ARRAYS')
    for name in ('facesToken', 'goalArrayToken', 'reachedTerminalsArrayToken',
                 'startPadToken', 'rootPadToken'):
        need(integer(source[name], 1, 2**31-1), 'GRAPH_SOURCE_TOKEN')
    eligible_pth = set(branch['reachedPadIds']) - {branch['rootPadId'], branch['startPadId']}
    need(set(source['reachedPthPadIds']) <= eligible_pth
         and all(value in pads_by_id and pads_by_id[value]['netId'] == branch['netId']
                 for value in source['reachedPthPadIds']), 'GRAPH_PTH_SUBSET')
    if header['mode'] == 'AUTHORED':
        need(source['reachedPthPadIds'] == [], 'GRAPH_AUTHORED_PTH_CENSUS')

    pub_tokens = {value for value in objects.values() if value is not None}
    node_tokens = set(publisher['path']['nodeTokens'])
    endpoint_tokens = (source['startPadToken'], source['rootPadToken'])
    container_tokens = (source['facesToken'], source['routerTokens'][0],
                        source['routerTokens'][1], source['goalArrayToken'],
                        source['reachedTerminalsArrayToken'])
    need(len(set(container_tokens)) == 5
         and not (set(container_tokens) & (pub_tokens | node_tokens | set(endpoint_tokens))),
         'GRAPH_CONTAINER_TOKEN_COLLISION')
    need(source['startPadToken'] != source['rootPadToken'], 'GRAPH_ENDPOINT_TOKEN_ALIAS')
    for pad_token, pad_id in zip(endpoint_tokens, (branch['startPadId'], branch['rootPadId'])):
        if branch['foundTerminalPadId'] == pad_id:
            need(objects['terminal'] is not None and pad_token == objects['terminal'],
                 'GRAPH_TERMINAL_TOKEN_JOIN')
        else:
            need(pad_token not in pub_tokens, 'GRAPH_PAD_TOKEN_COLLISION')
        need(pad_token not in node_tokens, 'GRAPH_NODE_TOKEN_COLLISION')

    phase_work = document['phaseWork']
    need(type(phase_work) is list and len(phase_work) == 32
         and all(integer(value) for value in phase_work), 'GRAPH_PHASE_WORK_SCHEMA')
    # Keep every charged Cursor stage from the frozen owner.  COMPLETE is an
    # explicit stage but cannot consume a charged unit; unassigned indices are
    # unused in this owner revision.
    active_phases = {0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                     18, 19, 20, 22, 24, 25, 26, 28, 29}
    need(all(value == 0 for index, value in enumerate(phase_work)
             if index not in active_phases), 'GRAPH_UNUSED_PHASE_WORK')

    body = document['body']
    keys(body, ('schema', 'stability', 'grid', 'path', 'work', 'masks'), 'GRAPH_BODY_SCHEMA')
    need(body['schema'] == GRAPH_BODY_SCHEMA, 'GRAPH_BODY_IDENTITY')
    stability = body['stability']
    stability_names = ('prefixGeometrySha256', 'liveBeforeVerified', 'liveAfterVerified',
                       'liveEffectsUnchanged', 'goalVerified', 'originalPathVerified')
    keys(stability, stability_names, 'GRAPH_STABILITY_SCHEMA')
    need(digest(stability['prefixGeometrySha256'])
         and all(type(stability[name]) is bool and stability[name] is True
                 for name in stability_names[1:]), 'GRAPH_STABILITY_VALUES')
    pub_before, pub_after = publisher['before'], publisher['after']
    need(stability['prefixGeometrySha256'] == pub_before['geometrySha256']
         == pub_after['geometrySha256'] == branch_row['prefixSha256']
         and pub_before['geometryBytes'] == pub_after['geometryBytes'] == branch_row['prefixBytes'],
         'GRAPH_PREFIX_GEOMETRY_JOIN')

    grid = body['grid']
    grid_names = ('width', 'height', 'cells', 'originX', 'originY', 'pitch', 'startCell',
                  'initialLayerMask', 'endCell', 'endLayer', 'goalUnionCellCount')
    keys(grid, grid_names, 'GRAPH_GRID_SCHEMA')
    pub_grid = publisher['grid']
    need(all(integer(grid[name], 1, LIMITS['cells']) for name in ('width', 'height', 'cells'))
         and grid['cells'] == grid['width'] * grid['height']
         and integer(grid['originX'], -1_000_000, 1_000_000)
         and integer(grid['originY'], -1_000_000, 1_000_000)
         and integer(grid['pitch'], 10, 10)
         and integer(grid['startCell'], 0, grid['cells']-1)
         and integer(grid['endCell'], 0, grid['cells']-1)
         and integer(grid['endLayer'], 0, 1)
         and integer(grid['goalUnionCellCount'], 1, grid['cells'])
         and (grid['width'], grid['height'], grid['cells'], grid['originX'], grid['originY'],
              grid['pitch']) == (pub_grid['width'], pub_grid['height'], pub_grid['cells'],
                                 pub_grid['originX'], pub_grid['originY'], pub_grid['pitch'])
         and grid['initialLayerMask'] == source['initialLayerMask'] == 3,
         'GRAPH_GRID_VALUES')
    nodes = publisher['path']['nodes']
    need(grid['startCell'] == nodes[0][0] and grid['endCell'] == nodes[-1][0]
         and grid['endLayer'] == nodes[-1][1], 'GRAPH_GRID_PATH_JOIN')

    masks = body['masks']
    keys(masks, ('topStepsHex', 'bottomStepsHex', 'goalFacesHex'), 'GRAPH_MASK_SCHEMA')
    for name in ('topStepsHex', 'bottomStepsHex'):
        value = masks[name]
        need(type(value) is str and len(value) == grid['cells']
             and re.fullmatch('[0-9a-f]{' + str(grid['cells']) + '}', value) is not None,
             'GRAPH_STEP_MASK')
    goal_faces = masks['goalFacesHex']
    need(type(goal_faces) is str and len(goal_faces) == grid['cells']
         and re.fullmatch('[0-3]{' + str(grid['cells']) + '}', goal_faces) is not None,
         'GRAPH_GOAL_MASK')
    need(digest(document['goalFacesSha256'])
         and document['goalFacesSha256'] == sha(goal_faces.encode('ascii')),
         'GRAPH_GOAL_HASH')
    goal_values = [ord(value) - 48 for value in goal_faces]
    need(sum(value != 0 for value in goal_values) == grid['goalUnionCellCount']
         and sum(bool(value & 1) for value in goal_values) == branch_row['snapshot']['goalsTop']
         and sum(bool(value & 2) for value in goal_values) == branch_row['snapshot']['goalsBottom']
         and goal_values[grid['endCell']] & (1 << grid['endLayer']),
         'GRAPH_GOAL_BRANCH_JOIN')
    for mask in (masks['topStepsHex'], masks['bottomStepsHex']):
        for cell, character in enumerate(mask):
            flags = int(character, 16)
            x, y = cell % grid['width'], cell // grid['width']
            need(not (y == 0 and flags & 1) and not (x + 1 == grid['width'] and flags & 2)
                 and not (y + 1 == grid['height'] and flags & 4)
                 and not (x == 0 and flags & 8), 'GRAPH_STEP_BOUNDARY')

    graph_path = body['path']
    keys(graph_path, ('nodeCount', 'planarEdgeCount', 'transitionCount', 'tupleSha256'),
         'GRAPH_PATH_SCHEMA')
    need(integer(graph_path['nodeCount'], 1, LIMITS['pathNodes'])
         and integer(graph_path['planarEdgeCount'], 0, LIMITS['pathNodes']-1)
         and integer(graph_path['transitionCount'], 0, LIMITS['transitionCap'])
         and digest(graph_path['tupleSha256'])
         and graph_path['nodeCount'] == len(nodes), 'GRAPH_PATH_VALUES')
    planar_edges = sum(left[1] == right[1] for left, right in zip(nodes, nodes[1:]))
    transitions = len(nodes) - 1 - planar_edges
    tuple_rows = []
    for index, node in enumerate(nodes):
        cell, layer, x, y, direction, vias, cost, heuristic, serial, key_value, parent = node
        tuple_rows.append(','.join(str(value) for value in
                          (index, parent, cell, x, y, layer, direction, vias, cost,
                           heuristic, serial, key_value)))
        if index:
            previous = nodes[index - 1]
            if layer == previous[1]:
                dx, dy = x - previous[2], y - previous[3]
                bit = (1 if (dx, dy) == (0, -grid['pitch']) else
                       2 if (dx, dy) == (grid['pitch'], 0) else
                       4 if (dx, dy) == (0, grid['pitch']) else
                       8 if (dx, dy) == (-grid['pitch'], 0) else 0)
                need(bit != 0 and int(masks['topStepsHex' if layer == 0 else
                                              'bottomStepsHex'][previous[0]], 16) & bit,
                     'GRAPH_PATH_DIRECTED_EDGE')
    tuple_preimage = '\n'.join(tuple_rows).encode('ascii')
    need(graph_path['planarEdgeCount'] == planar_edges
         and graph_path['transitionCount'] == transitions and transitions == 0
         and graph_path['tupleSha256'] == sha(tuple_preimage), 'GRAPH_PATH_HASH_OR_COUNT')

    work = body['work']
    work_names = ('advanceCalls', 'workSteps', 'copyEntries', 'comparisonEntries',
                  'geometryBytes', 'effectUnits', 'ordinaryQueries', 'goalCells',
                  'reachedPadChecks', 'encodedCells', 'pathNodes', 'pathEdgesVerified',
                  'tupleBytes', 'serializedUtf16Units', 'activeMillis')
    keys(work, work_names, 'GRAPH_WORK_SCHEMA')
    need(integer(entry['elapsedToCloseNanos'], 1, LIMITS['operationNanos'])
         and all(integer(work[name]) for name in work_names)
         and work['advanceCalls'] > 0 and work['workSteps'] > 0
         and work['copyEntries'] > 0 and work['comparisonEntries'] > 0,
         'GRAPH_WORK_VALUES')
    cursor_groups = [group for group in groups if group['graphCursorSteps'] > 0]
    cursor_steps = sum(group['graphCursorSteps'] for group in groups)
    need(work['advanceCalls'] == len(cursor_groups)
         and work['workSteps'] == cursor_steps == sum(phase_work)
         and work['ordinaryQueries'] == 2 * (
             2 * grid['height'] * (grid['width'] - 1)
             + 2 * grid['width'] * (grid['height'] - 1))
         and work['goalCells'] == grid['cells']
         and work['encodedCells'] == 3 * grid['cells']
         and work['pathNodes'] == graph_path['nodeCount']
         and work['pathEdgesVerified'] == graph_path['nodeCount'] - 1
         and work['geometryBytes'] == 2 * pub_before['geometryBytes']
         and work['tupleBytes'] == len(tuple_preimage)
         and work['serializedUtf16Units'] <= 1_000_000
         and work['activeMillis'] <= entry['elapsedToCloseNanos'] // 1_000_000,
         'GRAPH_WORK_CONSERVATION')
    lower_bound_names = ('copyEntries', 'comparisonEntries', 'geometryBytes', 'effectUnits',
                         'ordinaryQueries', 'goalCells', 'reachedPadChecks', 'pathNodes',
                         'pathEdgesVerified', 'tupleBytes', 'serializedUtf16Units')
    need(work['workSteps'] >= sum(work[name] for name in lower_bound_names),
         'GRAPH_WORK_LOWER_BOUND')
    need(work['advanceCalls'] == len(cursor_groups) and all(
             group['graphCursorSteps'] <= 32768 for group in cursor_groups),
         'GRAPH_CURSOR_GROUP_CONSERVATION')

    body_raw = _top_level_value_bytes(raw, 'body')
    canonical_body = _canonical_graph_body(body)
    need(body_raw == canonical_body and work['serializedUtf16Units'] == len(body_raw),
         'GRAPH_BODY_CANONICAL_BYTES')

    integrity = document['integrity']
    integrity_names = ('nativeSourceBound', 'parentPaused', 'retainedSourceUnchanged',
                       'cursorSourcesReleased', 'producerSourcesReleased')
    keys(integrity, integrity_names, 'GRAPH_INTEGRITY_SCHEMA')
    need(all(type(integrity[name]) is bool and integrity[name] is True
             for name in integrity_names), 'GRAPH_INTEGRITY')
    limitations = document['limitations']
    limitations_names = ('model', 'lengthEligibilityOnly', 'legalSearchHistory',
                         'publicationQualified', 'layoutQualification', 'routeAdoption',
                         'playable100', 'fullPhysicalProof')
    keys(limitations, limitations_names, 'GRAPH_LIMITATIONS_SCHEMA')
    need(limitations['model'] == 'DIRECTED_PER_FACE_ORDINARY_EDGES'
         and type(limitations['lengthEligibilityOnly']) is bool
         and limitations['lengthEligibilityOnly'] is True
         and limitations['legalSearchHistory'] == 'NOT_RUN'
         and type(limitations['publicationQualified']) is bool
         and limitations['publicationQualified'] is False
         and type(limitations['layoutQualification']) is bool
         and limitations['layoutQualification'] is False
         and type(limitations['routeAdoption']) is bool
         and limitations['routeAdoption'] is False
         and type(limitations['playable100']) is int and limitations['playable100'] == 0
         and limitations['fullPhysicalProof'] == 'NOT_RUN',
         'GRAPH_LIMITATIONS')

    if header['mode'] == 'AUTHORED':
        need(context['operation'] == 4 and context['attempt'] == 4 and context['request'] == 0
             and context['pass'] == 1
             and (context['rootSeed'], context['placementSeed'], context['routingSeed'])
                 == ('9007199254740993', '-6587958367974952701', '8445301674744508279')
             and (branch['ordinal'], branch['netVectorPosition'], branch['netId'],
                  branch['startPadId'], branch['rootPadId'], branch['foundTerminalPadId'],
                  branch['branchRecordGroup'], branch['reachedPadIds'])
                 == (248, 28, 'OUT_F', 'RSH_F.1', 'CSTORE_F.2', 'CSTORE_F.2', 57,
                     ['CSTORE_F.2'])
             and branch_row['snapshot']['terminalReason'] == 'FOUND'
             and (source['target'], source['sourceKind'], source['policy'],
                  source['initialLayerMask'], source['reachedPthPadIds'])
                 == ('OUT_F', 'OUT_F_REJECTED_PUBLISHER_PATH', 'FULLER_TWO_LAYER', 3, [])
             and (grid['width'], grid['height'], grid['cells'], grid['originX'],
                  grid['originY'], grid['pitch']) == (478, 309, 147702, 30, 30, 10)
             and pub_before == pub_after
             and len(pub_before['traces']) == 180 and len(pub_before['holes']) == 48
             and pub_before['geometryBytes'] == 271606
             and pub_before['geometrySha256']
                 == 'cbc98315295693be7cddc31d0327f6f89e5819fedb2b4ad1d65a25e7decc2739'
             and (graph_path['nodeCount'], graph_path['planarEdgeCount'],
                  graph_path['transitionCount']) == (171, 170, 0)
             and (work['ordinaryQueries'], work['goalCells']) == (1178468, 147702),
             'GRAPH_AUTHORED_IDENTITY')
        start_pad = pads_by_id[branch['startPadId']]
        found_node = nodes[-1]
        path_length = sum(grid['pitch'] for left, right in zip(nodes, nodes[1:])
                          if left[1] == right[1])
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
        need((path_length, bends, direct, grid['endLayer']) == (1700, 4, 320, 1),
             'GRAPH_AUTHORED_PATH_METRICS')
    else:
        need(header['mode'] == 'TINY_CANARY', 'GRAPH_FIXTURE_MODE')

    diagnostics = analyze_graph(document)
    serialized_bytes = len(raw) + sum(len(line) for _row, line, _decoded in graph_chunks) \
        + len(result_line)
    capture_hash_bytes = len(raw) + sum(len(line) for _row, line, _decoded in graph_chunks) \
        + len(result_line)
    return dict(document=document, payloadBytes=len(raw), payloadSha256=sha(raw),
                serializedBytes=serialized_bytes, captureHashBytes=capture_hash_bytes,
                diagnostics=diagnostics)


def validate_pad_census(pads, candidate):
    need([p['padId'] for p in pads]==sorted({p['padId'] for p in pads})
         and len({p['netId'] for p in pads})==candidate['netCount']
         and len({p['componentId'] for p in pads})==candidate['componentCount'], 'PAD_CENSUS')


def validate_snapshot_shape(snapshot):
    keys(snapshot, SNAP_INTS+SNAP_BOOLS+SNAP_STRINGS, 'SNAPSHOT_SCHEMA')
    need(all(token(snapshot[n]) for n in ('netId','startPadId','rootPadId'))
         and type(snapshot['terminalReason']) is str, 'SNAPSHOT_IDENTIFIERS')
    need(all(integer(snapshot[n],-2**31,2**31-1) for n in SNAP_INTS)
         and all(type(snapshot[n]) is bool for n in SNAP_BOOLS), 'SNAPSHOT_TYPES')
    need(all(integer(snapshot[n],0,2**31-1) for n in SNAP_INTS if n not in
             ('startX','startY','rootX','rootY','initialManhattanDistance',
              'minimumExploredManhattanDistance'))
         and integer(snapshot['initialManhattanDistance'],-1,2**31-1)
         and integer(snapshot['minimumExploredManhattanDistance'],-1,2**31-1),
         'SNAPSHOT_COUNT_DOMAIN')


def audit_choices(branches, pads, arm):
    by_id = {p['padId']:p for p in pads}
    nets = {}
    for p in pads: nets.setdefault(p['netId'],[]).append(p['padId'])
    states = {}; seen = {}; prior = None; total = 0; choices = []
    for row in branches:
        s = row['snapshot']
        validate_snapshot_shape(s)
        p = s['pass']; net = s['netId']
        need(integer(p,0,2) and net in nets and s['terminalReason'] in
             ('FOUND','NO_PATH','TOTAL_SEARCH_LIMIT','BRANCH_SEARCH_LIMIT'), 'BRANCH_TERMINAL')
        need(integer(s['gridWidth'],1,250000) and integer(s['gridHeight'],1,250000)
             and s['gridWidth']*s['gridHeight'] <= 250000 and integer(s['prefixViaCount'],0,48),
             'BRANCH_GRID_HOLES')
        need(integer(s['branchExpanded'],0,100000) and integer(s['totalExpanded'],0,1000000)
             and integer(s['expandedTop']) and integer(s['expandedBottom'])
             and s['expandedTop']+s['expandedBottom'] == s['branchExpanded'], 'BRANCH_EXPANSIONS')
        total += s['branchExpanded']; need(total == s['totalExpanded'], 'CUMULATIVE_EXPANSIONS')
        if prior:
            ps = prior['snapshot']; need(p >= ps['pass'], 'PASS_ORDER')
            if p == ps['pass']:
                need(ps['terminalReason'] == 'FOUND', 'FOLLOWED_FAILED_BRANCH')
                old = states[(p,ps['netId'])]
                old['reached'].append(ps['startPadId'])
                if net != ps['netId']:
                    need(not old['remaining'], 'LEFT_UNFINISHED_NET')
        key = (p,net)
        if key not in states:
            need(net not in seen.setdefault(p,set()), 'NET_REVISIT')
            seen[p].add(net)
            ordered = sorted(nets[net]); need(len(ordered)>1, 'SINGLE_PAD_BRANCH')
            states[key] = {'reached':[ordered[0]],'remaining':ordered[1:]}
        else:
            need(prior is not None and prior['snapshot']['pass']==p
                 and prior['snapshot']['netId']==net, 'NET_REVISIT')
        state = states[key]; remaining = state['remaining']; reached = state['reached']
        need(remaining and s['rootPadId']==sorted(nets[net])[0]
             and s['reachedPadCount']==len(reached), 'BRANCH_ROOT_REACHED')
        distances = {a:min(abs(by_id[a]['x']-by_id[b]['x'])+abs(by_id[a]['y']-by_id[b]['y'])
                           for b in reached) for a in remaining}
        nearest = min(remaining,key=lambda a:(distances[a],a))
        need(s['startPadId']==nearest, 'INDEPENDENT_CHOICE')
        for which in ('start','root'):
            pad = by_id.get(s[which+'PadId'])
            need(pad is not None and pad['netId']==net
                 and (s[which+'X'],s[which+'Y'])==(pad['x'],pad['y']), 'BRANCH_ENDPOINT')
        choices.append(dict(nearest=nearest,reached=tuple(reached),
                            remaining=tuple(remaining)))
        remaining.remove(nearest); prior = row
    return choices, total


def comparable_diagnostic_clone(row):
    return {k:v for k,v in row.items() if k not in ('operation','group')}


def validate_diagnostic_clones(events, branches, branch_positions, pads, groups, arm):
    need(arm != 0 or not events, 'DIAGNOSTIC_CLONE_PLAIN')
    pad_by_id = {row['padId']:row for row in pads}
    pads_by_net = {}
    for row in pads:
        pads_by_net.setdefault(row['netId'], set()).add(row['padId'])
    groups_by_id = {row['ordinal']:row for row in groups}
    seen_keys = set(); matched = {i:[] for i in range(len(branches))}
    for position,row in events:
        key=(row['pass'],row['netId'],row['startPadId'],row['rootPadId'],row['faceIndex'])
        need(key not in seen_keys, 'DIAGNOSTIC_CLONE_DUPLICATE')
        seen_keys.add(key)
        need(row['netId'] in pads_by_net
             and row['startPadId'] in pad_by_id and row['rootPadId'] in pad_by_id
             and pad_by_id[row['startPadId']]['netId']==row['netId']
             and pad_by_id[row['rootPadId']]['netId']==row['netId'],
             'DIAGNOSTIC_CLONE_CENSUS')
        group=groups_by_id.get(row['group'])
        need(group is not None and group['phase']=='ADVANCE' and group['p07Slices']==1
             and group['accounting']=='MEASURED' and row['ownerSteps']<=group['p07Steps'],
             'DIAGNOSTIC_CLONE_GROUP')
        candidates=[]
        for i,branch in enumerate(branches):
            snapshot=branch['snapshot']
            if (snapshot['pass'],snapshot['netId'],snapshot['startPadId'],snapshot['rootPadId'],
                snapshot['prefixViaCount']) == (
                    row['pass'],row['netId'],row['startPadId'],row['rootPadId'],
                    row['prefixViaCount']):
                candidates.append(i)
        need(len(candidates)==1, 'DIAGNOSTIC_CLONE_BRANCH_MATCH')
        branch_index=candidates[0]
        need(position < branch_positions[branch_index], 'DIAGNOSTIC_CLONE_ORDER')
        matched[branch_index].append((position,row))

    for i,branch in enumerate(branches):
        snapshot=branch['snapshot']
        branch_events=matched[i]
        if snapshot['relaxedBoundStarted']:
            need(snapshot['relaxedBoundComplete'] is True
                 and all(snapshot[name] is True for name in DIAGNOSTIC_BOUND_PROOFS),
                 'DIAGNOSTIC_CLONE_PROOF')
        expected=(snapshot['relaxedBoundStarted'] and snapshot['prefixViaCount']<LIMITS['holeCap'])
        if expected:
            need([row['faceIndex'] for _,row in branch_events]==[0,1]
                 and sum(row['ownerSteps'] for _,row in branch_events)==2,
                 'DIAGNOSTIC_CLONE_PAIR')
        else:
            need(not branch_events, 'DIAGNOSTIC_CLONE_UNEXPECTED')
    need(len(events)<=2*3*(len(pads)-len(pads_by_net)), 'DIAGNOSTIC_CLONE_CAP')


def validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind):
    need(raw and len(raw)<=LIMITS['receiptBytes'] and raw.endswith(b'\n') and b'\r' not in raw,
         'RECEIPT_ENCODING_SIZE')
    lines = raw.splitlines(keepends=True)
    need(all(len(x)<=LIMITS['lineBytes'] and x!=b'\n' for x in lines), 'LINE_SIZE_EMPTY')
    rows = [strict_json(x[:-1]) for x in lines]
    h = rows[0]
    keys(h, ('record','group','schema','runId','mode','operation','arm','sourceManifestSha256',
             'wireSha256','candidate','limits'), 'HEADER_SCHEMA')
    need(h['record']=='HEADER' and integer(h['group'],0,0) and h['schema']==SCHEMA and h['runId']==run_id
         and integer(h['operation'],0,4) and h['operation']==arm and h['sourceManifestSha256']==source_sha
         and h['wireSha256']==wire_sha and h['limits']==LIMITS, 'HEADER_IDENTITY')
    keys(h['arm'],('id','api','strategy','netOrderStrategy','observed','publisherCapture',
                   'graphCapture'),'ARM_SCHEMA')
    need(tuple(h['arm'][x] for x in
               ('id','api','strategy','netOrderStrategy','observed','publisherCapture'))==ARMS[arm]
         and type(h['arm']['observed']) is bool
         and type(h['arm']['publisherCapture']) is bool
         and type(h['arm']['graphCapture']) is bool, 'ARM_IDENTITY')
    graph_capture_enabled = h['arm']['graphCapture']
    if arm != 4 or kind != 'READER_FIXTURE':
        need(graph_capture_enabled is (arm == 4 and kind == 'AUTHORED'),
             'ARM_GRAPH_CAPTURE')
    c = h['candidate']
    keys(c,('rootSeed','placementSeed','routingSeed','variant','layoutVersion','dependencyEpoch',
            'componentCount','padCount','netCount','traceWidthProfile'),'CANDIDATE_SCHEMA')
    for name in ('rootSeed','placementSeed','routingSeed'):
        need(type(c[name]) is str and re.fullmatch(r'0|-?[1-9][0-9]*',c[name]) is not None
             and -2**63<=int(c[name])<2**63, 'SEED_TRANSPORT')
    need(c['layoutVersion']==26 and c['dependencyEpoch']=='tsj-generation-dependencies-v28'
         and c['traceWidthProfile']=='ROLE_WIDTHS' and integer(c['padCount'],2,1024)
         and integer(c['netCount'],1,c['padCount']-1)
         and integer(c['componentCount'],1,c['padCount']), 'CANDIDATE_VALUES')
    if kind=='AUTHORED':
        need(h['mode']=='AUTHORED' and tuple(c[x] for x in ('rootSeed','placementSeed','routingSeed'))
             ==('9007199254740993','-6587958367974952701','8445301674744508279')
             and c['variant']=='SEPARATE' and c['componentCount']==100 and c['padCount']==246,
             'AUTHORED_CANDIDATE')
    else:
        need(h['mode']=='TINY_CANARY' and c['variant']=='TINY'
             and tuple(c[x] for x in ('rootSeed','placementSeed','routingSeed'))
             == ('42', '5526715536693200536', '4493419427463069983'),
             'TINY_CANDIDATE')
    pads=[]; branches=[]; groups=[]; result=None; current_group=0; group_bytes=0
    group_callbacks=0; group_pads=0; group_diagnostic_clones=0; group_net_orders=0
    group_publisher_chunks=0; group_publisher_results=0
    branch_positions=[]; diagnostic_events=[]; net_order_rows=[]; net_order_positions=[]
    publisher_chunks=[]; publisher_result=None; publisher_result_line=None
    publisher_payload_bytes=0
    graph_chunks=[]; graph_result=None; graph_result_line=None; graph_payload_bytes=0
    publisher_output_started=False; publisher_capture_closed=False
    graph_output_started=False; graph_capture_closed=False
    graph_cursor_started=False
    group_graph_chunks=0; group_graph_results=0
    branch_seen=False; report_seen=False; report_closed=False
    pass_one_seen=False
    for line_index,(row,line) in enumerate(zip(rows[:-1],lines[:-1])):
        need(not report_closed, 'TRAILING_REPORT_RECORD')
        need(type(row) is dict and type(row.get('record')) is str, 'RECORD_TYPE')
        typ=row['record']
        if result is not None:
            need(typ == 'GROUP', 'CAPTURE_AFTER_RESULT')
        source_record = typ in ('PAD', 'BRANCH', 'NET_ORDER', 'DIAGNOSTIC_CLONE')
        if graph_capture_enabled and graph_cursor_started and not graph_capture_closed:
            need(not source_record, 'GRAPH_CAPTURE_SOURCE_INTERLEAVING')
        capture_closed = graph_capture_closed if graph_capture_enabled else publisher_capture_closed
        if (publisher_output_started or publisher_result is not None) and not capture_closed:
            allowed_output = (('PUB_CHUNK', 'PUB_RESULT', 'GRAPH_CHUNK', 'GRAPH_RESULT',
                               'RESULT', 'GROUP') if graph_capture_enabled else
                              ('PUB_CHUNK', 'PUB_RESULT', 'RESULT', 'GROUP'))
            need(typ in allowed_output, 'CAPTURE_OUTPUT_INTERLEAVING')
        if typ=='HEADER': need(row is h,'EXTRA_HEADER')
        elif typ=='PAD':
            keys(row,('record','group','padId','netId','componentId','x','y','role'),'PAD_SCHEMA')
            need(not branch_seen and not report_seen and all(token(row[n]) for n in
                 ('padId','netId','componentId','role')) and integer(row['x'],-1_000_000,1_000_000)
                 and integer(row['y'],-1_000_000,1_000_000), 'PAD_VALUES_ORDER')
            pads.append(row); group_pads+=1
        elif typ=='BRANCH':
            keys(row,('record','operation','ordinal','group','prefixSha256','prefixBytes','snapshot'),
                 'BRANCH_SCHEMA')
            need(not report_seen and arm!=0 and integer(row['operation'],0,4) and row['operation']==arm
                 and integer(row['ordinal']) and row['ordinal']==len(branches) and digest(row['prefixSha256'])
                 and integer(row['prefixBytes'],1,LIMITS['prefixBytes']), 'BRANCH_IDENTITY')
            validate_snapshot_shape(row['snapshot'])
            branches.append(row);branch_positions.append(line_index)
            group_callbacks+=1;branch_seen=True
            if type(row.get('snapshot')) is dict and row['snapshot'].get('pass') == 1:
                pass_one_seen=True
                need(len(net_order_rows)==1 and net_order_positions[0] < line_index,
                     'NET_ORDER_MISSING_OR_LATE')
        elif typ=='NET_ORDER':
            keys(row,NET_ORDER_FIELDS,'NET_ORDER_SCHEMA')
            need(not net_order_rows, 'NET_ORDER_DUPLICATE')
            need(not report_seen and arm in (1,2,3,4) and not pass_one_seen
                 and len(pads)==c['padCount'], 'NET_ORDER_POSITION')
            validate_pad_census(pads,c)
            need(integer(row['operation'],1,4) and row['operation']==arm
                 and integer(row['group'],0,639) and row['group']==current_group
                 and integer(row['pass'],1,1) and row['strategy']==ARMS[arm][3],
                 'NET_ORDER_IDENTITY')
            need(row['promotedNet'] is None or token(row['promotedNet']),
                 'NET_ORDER_PROMOTION_TYPE')
            need(integer(row['completedOrderings'],1,1)
                 and integer(row['yieldedOrderings'],0,1)
                 and digest(row['prefixSha256'])
                 and integer(row['prefixBytes'],1,LIMITS['prefixBytes']),
                 'NET_ORDER_VALUES')
            net_ids=row['netIds']
            need(type(net_ids) is list and all(token(net) for net in net_ids)
                 and len(net_ids)==len(set(net_ids))
                 and set(net_ids)=={pad['netId'] for pad in pads}, 'NET_ORDER_CENSUS')
            net_order_rows.append(row);net_order_positions.append(line_index)
            group_net_orders+=1
        elif typ=='PUB_CHUNK':
            keys(row, PUB_CHUNK_FIELDS, 'PUB_CHUNK_SCHEMA')
            need(arm == 4 and result is None and publisher_result is None
                 and not graph_output_started
                 and row['operation'] == 4 and row['targetId'] == PUBLISHER_TARGET_ID
                 and integer(row['group'], 0, 639) and row['group'] == current_group
                 and integer(row['index'], 0, LIMITS['publisherPayloadBytes'])
                 and integer(row['offset'], 0, LIMITS['publisherPayloadBytes'])
                 and integer(row['bytes'], 1, LIMITS['publisherChunkBytes'])
                 and type(row['data']) is str, 'PUB_CHUNK_IDENTITY')
            need(row['index'] == len(publisher_chunks)
                 and row['offset'] == publisher_payload_bytes, 'PUB_CHUNK_ORDER')
            try:
                encoded = row['data'].encode('ascii')
                decoded = base64.b64decode(encoded, validate=True)
            except (UnicodeEncodeError, ValueError):
                raise ValueError('PUB_CHUNK_BASE64')
            need(base64.b64encode(decoded).decode('ascii') == row['data']
                 and len(decoded) == row['bytes'], 'PUB_CHUNK_BASE64')
            publisher_payload_bytes += len(decoded)
            need(publisher_payload_bytes <= LIMITS['publisherPayloadBytes'],
                 'PUB_PAYLOAD_LIMIT')
            publisher_chunks.append((row, line, decoded))
            publisher_output_started = True
            group_publisher_chunks += 1
        elif typ=='PUB_RESULT':
            keys(row, PUB_RESULT_FIELDS, 'PUB_RESULT_SCHEMA')
            need(arm == 4 and result is None and publisher_result is None
                 and not graph_output_started
                 and row['operation'] == 4 and row['targetId'] == PUBLISHER_TARGET_ID
                 and integer(row['group'], 0, 639) and row['group'] == current_group
                 and integer(row['payloadBytes'], 1, LIMITS['publisherPayloadBytes'])
                 and digest(row['payloadSha256'])
                 and integer(row['chunkCount'], 1, LIMITS['publisherPayloadBytes']),
                 'PUB_RESULT_IDENTITY')
            need(publisher_chunks and row['chunkCount'] == len(publisher_chunks)
                 and row['payloadBytes'] == publisher_payload_bytes,
                 'PUB_RESULT_COUNTS')
            publisher_result = row
            publisher_result_line = line
            group_publisher_results += 1
        elif typ=='GRAPH_CHUNK':
            keys(row, GRAPH_CHUNK_FIELDS, 'GRAPH_CHUNK_SCHEMA')
            need(graph_capture_enabled and arm == 4 and result is None
                 and publisher_result is not None and graph_result is None
                 and row['record'] == 'GRAPH_CHUNK'
                 and integer(row['operation'], 4, 4) and row['targetId'] == GRAPH_TARGET_ID
                 and integer(row['group'], 0, 639) and row['group'] == current_group
                 and integer(row['index'], 0, LIMITS['publisherPayloadBytes'])
                 and integer(row['offset'], 0, LIMITS['publisherPayloadBytes'])
                 and integer(row['bytes'], 1, LIMITS['publisherChunkBytes'])
                 and type(row['data']) is str, 'GRAPH_CHUNK_IDENTITY')
            need(row['index'] == len(graph_chunks)
                 and row['offset'] == graph_payload_bytes, 'GRAPH_CHUNK_ORDER')
            try:
                encoded = row['data'].encode('ascii')
                decoded = base64.b64decode(encoded, validate=True)
            except (UnicodeEncodeError, ValueError):
                raise ValueError('GRAPH_CHUNK_BASE64')
            need(base64.b64encode(decoded).decode('ascii') == row['data']
                 and len(decoded) == row['bytes'], 'GRAPH_CHUNK_BASE64')
            graph_payload_bytes += len(decoded)
            need(graph_payload_bytes <= LIMITS['publisherPayloadBytes'], 'GRAPH_PAYLOAD_LIMIT')
            graph_chunks.append((row, line, decoded))
            group_graph_chunks += 1
            graph_output_started = True
        elif typ=='GRAPH_RESULT':
            keys(row, GRAPH_RESULT_FIELDS, 'GRAPH_RESULT_SCHEMA')
            need(graph_capture_enabled and arm == 4 and result is None
                 and publisher_result is not None and graph_result is None
                 and row['record'] == 'GRAPH_RESULT'
                 and integer(row['operation'], 4, 4) and row['targetId'] == GRAPH_TARGET_ID
                 and integer(row['group'], 0, 639) and row['group'] == current_group
                 and integer(row['payloadBytes'], 1, LIMITS['publisherPayloadBytes'])
                 and digest(row['payloadSha256'])
                 and integer(row['chunkCount'], 1, LIMITS['publisherPayloadBytes']),
                 'GRAPH_RESULT_IDENTITY')
            need(graph_chunks and row['chunkCount'] == len(graph_chunks)
                 and row['payloadBytes'] == graph_payload_bytes,
                 'GRAPH_RESULT_COUNTS')
            graph_result = row
            graph_result_line = line
            group_graph_results += 1
            graph_output_started = True
        elif typ=='DIAGNOSTIC_CLONE':
            keys(row,DIAGNOSTIC_CLONE_FIELDS,'DIAGNOSTIC_CLONE_SCHEMA')
            need(arm in (1,2,3,4) and integer(row['operation'],1,4) and row['operation']==arm
                 and integer(row['ordinal']) and row['ordinal']==len(diagnostic_events)
                 and integer(row['pass'],0,2) and all(token(row[name]) for name in
                     ('netId','startPadId','rootPadId')),
                 'DIAGNOSTIC_CLONE_IDENTITY')
            need(integer(row['faceIndex'],0,1), 'DIAGNOSTIC_CLONE_FACE')
            need(integer(row['prefixViaCount'],0,LIMITS['holeCap']-1),
                 'DIAGNOSTIC_CLONE_CAPACITY')
            need(integer(row['ownerSteps']) and row['ownerSteps']==1,
                 'DIAGNOSTIC_CLONE_STEPS')
            diagnostic_events.append((line_index,row));group_diagnostic_clones+=1
        elif typ=='RESULT':
            keys(row,('record','operation','group','proof','route','context','receipt','outcome',
                      'winner','physical','logicalWork','boundary'),'RESULT_SCHEMA')
            need(result is None and integer(row['operation'],0,4) and row['operation']==arm
                 and row['proof']=='COMPLETE', 'RESULT_IDENTITY')
            need((arm == 4 and publisher_result is not None
                  and ((graph_capture_enabled and graph_result is not None)
                       or (not graph_capture_enabled and not graph_chunks
                           and graph_result is None)))
                 or (arm != 4 and not publisher_chunks and publisher_result is None
                     and not graph_chunks and graph_result is None),
                 'CAPTURE_RESULT_BEFORE_REQUEST_RESULT')
            result=row;report_seen=True
        elif typ=='GROUP':
            keys(row,GROUP_FIELDS,'GROUP_SCHEMA')
            need(integer(row['operation'],0,4) and row['operation']==arm
                 and integer(row['ordinal'],0,639) and row['ordinal']==current_group
                 and row['phase'] in ('SETUP','ADVANCE','PHYSICAL','CAPTURE','REPORT'),
                 'GROUP_IDENTITY')
            for n in ('elapsedBeforeOwnLineNanos','observerChecks','callbacks','censusRows',
                      'netOrderRows','hashInputBytes','serializedBeforeOwnLineBytes',
                      'diagnosticCloneSteps','publisherChunkRows','publisherResultRows',
                      'graphChunkRows','graphResultRows','graphCursorSteps',
                      'diagnosticCopiedEntries','diagnosticSerializedBytes',
                      'diagnosticHashBytes','diagnosticUnits'):
                need(integer(row[n]),'GROUP_NUMBER')
            need(row['elapsedBeforeOwnLineNanos']<=LIMITS['groupNanos']
                 and integer(row['generatorAdvances'],0,1) and integer(row['p07Slices'],0,1)
                 and row['p07Slices']<=row['generatorAdvances']
                 and row['graphCursorSteps']<=32768, 'GROUP_LIMIT')
            need(row['generatorAdvances']==(1 if row['phase']=='ADVANCE' else 0), 'GROUP_PHASE_WORK')
            if row['phase'] == 'CAPTURE':
                need(row['generatorAdvances'] == 0 and row['p07Slices'] == 0
                     and row['accounting'] == 'NOT_APPLICABLE' and row['p07Steps'] is None
                     and row['observerChecks'] == 0 and row['callbacks'] == 0
                     and row['censusRows'] == 0 and row['netOrderRows'] == 0
                     and row['diagnosticCloneSteps'] == 0, 'CAPTURE_OWNER_WORK')
                need(arm == 4 and row['diagnosticUnits'] <= 32768,
                     'CAPTURE_DIAGNOSTIC_LIMIT')
                need((row['graphChunkRows'] == 0 and row['graphResultRows'] == 0
                      and row['graphCursorSteps'] == 0)
                     or (graph_capture_enabled and arm == 4),
                     'GRAPH_CAPTURE_GROUP')
                if row['graphCursorSteps']:
                    need(group_publisher_chunks == 0 and group_publisher_results == 0
                         and group_graph_chunks == 0 and group_graph_results == 0,
                         'GRAPH_CURSOR_OUTPUT_ORDER')
            elif row['p07Slices']:
                need(row['accounting']=='MEASURED' and integer(row['p07Steps'],0,32768)
                     and row['observerChecks']>=1,'P07_ACCOUNTING')
            else:need(row['accounting']=='NOT_APPLICABLE' and row['p07Steps'] is None,'P07_ABSENCE')
            need(row['callbacks']==group_callbacks and row['censusRows']==group_pads
                 and row['netOrderRows']==group_net_orders
                 and row['publisherChunkRows']==group_publisher_chunks
                 and row['publisherResultRows']==group_publisher_results
                  and row['graphChunkRows']==group_graph_chunks
                  and row['graphResultRows']==group_graph_results
                 and row['serializedBeforeOwnLineBytes']==group_bytes, 'GROUP_RECORD_COUNTS')
            need(row['diagnosticUnits'] == row['graphCursorSteps']
                 + row['diagnosticCopiedEntries']
                 + row['diagnosticSerializedBytes'] + row['diagnosticHashBytes'],
                 'DIAGNOSTIC_UNIT_SUM')
            if arm != 4:
                need(row['publisherChunkRows'] == 0 and row['publisherResultRows'] == 0
                     and row['graphChunkRows'] == 0 and row['graphResultRows'] == 0
                     and row['graphCursorSteps'] == 0
                     and row['diagnosticCopiedEntries'] == 0
                     and row['diagnosticSerializedBytes'] == 0
                     and row['diagnosticHashBytes'] == 0
                     and row['diagnosticUnits'] == 0, 'PUB_NON_CAPTURE_ACCOUNTING')
            if row['phase'] == 'ADVANCE':
                need(row['diagnosticCopiedEntries'] <= LIMITS['snapshotEntries']
                     and row['diagnosticSerializedBytes'] == 0
                     and row['diagnosticHashBytes'] == 0, 'ADVANCE_DIAGNOSTIC_SCOPE')
            elif row['phase'] == 'CAPTURE':
                need(row['diagnosticUnits'] <= 32768, 'CAPTURE_DIAGNOSTIC_LIMIT')
            else:
                need(row['diagnosticCopiedEntries'] == 0
                     and row['graphChunkRows'] == 0 and row['graphResultRows'] == 0
                     and row['graphCursorSteps'] == 0
                     and row['diagnosticSerializedBytes'] == 0
                     and row['diagnosticHashBytes'] == 0
                     and row['diagnosticUnits'] == 0, 'DIAGNOSTIC_PHASE')
            if row['publisherChunkRows'] or row['publisherResultRows']:
                need(row['phase'] == 'CAPTURE', 'PUB_CAPTURE_PHASE')
            if row['graphChunkRows'] or row['graphResultRows'] or row['graphCursorSteps']:
                need(graph_capture_enabled and arm == 4 and row['phase'] == 'CAPTURE',
                     'GRAPH_CAPTURE_PHASE')
            if row['graphCursorSteps']:
                need(not publisher_output_started and not graph_output_started,
                     'GRAPH_CURSOR_RESTART')
            need(row['diagnosticCloneSteps']==group_diagnostic_clones,
                 'DIAGNOSTIC_CLONE_GROUP_COUNT')
            need(row['diagnosticCloneSteps']<=(row['p07Steps'] or 0),
                 'DIAGNOSTIC_CLONE_STEP_CAP')
            if group_diagnostic_clones:
                need(row['phase']=='ADVANCE' and row['p07Slices']==1
                     and row['accounting']=='MEASURED','DIAGNOSTIC_CLONE_GROUP')
            if group_callbacks or group_pads or group_net_orders:
                need(row['phase']=='ADVANCE' and row['p07Slices']==1,'LIVE_OWNER_OBSERVATION_GROUP')
            need(row['hashInputBytes']>=sum(b['prefixBytes'] for b in branches
                 if b['group']==current_group)+sum(order['prefixBytes'] for order in net_order_rows
                 if order['group']==current_group)+row['diagnosticHashBytes'],
                 'GROUP_HASH_BYTES')
            if report_seen:
                need(row['phase']=='REPORT','FINAL_GROUP_PHASE')
                report_closed=True
            groups.append(row);current_group+=1;group_bytes=0;group_callbacks=0;group_pads=0
            group_diagnostic_clones=0;group_net_orders=0
            group_publisher_chunks=0;group_publisher_results=0
            group_graph_chunks=0;group_graph_results=0
            if row['graphCursorSteps'] > 0:
                graph_cursor_started = True
            if row['publisherResultRows'] > 0:
                publisher_capture_closed = True
            if row['graphResultRows'] > 0:
                graph_capture_closed = True
            need(current_group<=640,'GROUP_COUNT');continue
        else:raise ValueError('UNEXPECTED_RECORD')
        need(integer(row.get('group'),0,639) and row['group']==current_group,'RECORD_GROUP')
        group_bytes+=len(line)
    f=rows[-1]
    keys(f,('record','group','operation','complete','padCount','branchCount','groupCount'),'FOOTER_SCHEMA')
    need(result is not None and groups and groups[-1]['phase']=='REPORT'
         and group_bytes==0 and f['record']=='FOOTER' and integer(f['operation'],0,4) and f['operation']==arm
         and integer(f['group'],0,639) and f['group']==len(groups)-1 and f['complete'] is True
         and all(integer(f[n]) for n in ('padCount','branchCount','groupCount'))
         and f['padCount']==len(pads)==c['padCount'] and f['branchCount']==len(branches)
         and f['groupCount']==len(groups),'FOOTER_CENSUS')
    validate_pad_census(pads,c)
    need(all(len({p['role'] for p in pads if p['netId']==net})==1
             for net in {p['netId'] for p in pads}), 'NET_ROLE_CENSUS')
    need(len(branches)<=3*(len(pads)-c['netCount']),'BRANCH_CAP')
    need(bool(net_order_rows)==pass_one_seen, 'NET_ORDER_PRESENCE')
    context=result['context']
    keys(context,('requestIndex','selectedAttempt','eligibleAttempts','pristineSha256','pristineBytes',
                  'padCensusSha256','boardSha256'),'CONTEXT_SCHEMA')
    eligible=context['eligibleAttempts']
    need(integer(context['requestIndex'],0,0) and integer(context['selectedAttempt'],0,5)
         and type(eligible) is list and 1<=len(eligible)<=6
         and all(integer(x,0,5) for x in eligible) and len(set(eligible))==len(eligible)
         and eligible[0]==context['selectedAttempt'] and digest(context['pristineSha256'])
         and integer(context['pristineBytes'],1,LIMITS['prefixBytes'])
         and context['padCensusSha256']==sha(pad_bytes(pads)) and digest(context['boardSha256']),
         'CONTEXT_VALUES')
    if kind=='AUTHORED':need(context['selectedAttempt']==4,'ACTUAL_ATTEMPT')
    r=result['receipt'];validate_receipt(r)
    choices=[]
    if arm:
        choices,total=audit_choices(branches,pads,arm)
        need(total==r['expansions'],'RECEIPT_EXPANSION_SUM')
        total_limit_rows=[i for i,b in enumerate(branches)
                          if b['snapshot']['terminalReason']=='TOTAL_SEARCH_LIMIT']
        if total_limit_rows:
            need(total_limit_rows==[len(branches)-1] and r['termination']=='TOTAL_SEARCH_LIMIT'
                 and r['comparisonComplete'] is False, 'TOTAL_LIMIT_CONTINUATION')
        need({b['snapshot']['pass'] for b in branches}==set(range(r['startedOrderings'])),
             'MISSING_ORDERING_BRANCH_PROOF')
    net_order=None;net_order_audit=None
    if net_order_rows:
        net_order=net_order_rows[0]
        need(net_order_positions[0] > max(i for i,row in enumerate(rows)
             if row['record']=='PAD'), 'NET_ORDER_AFTER_CENSUS')
        pass_zero_positions=[position for position,branch in zip(branch_positions,branches)
                             if type(branch.get('snapshot')) is dict
                             and branch['snapshot'].get('pass')==0]
        need(pass_zero_positions and net_order_positions[0] > max(pass_zero_positions),
             'NET_ORDER_AFTER_PASS0')
        need(net_order['prefixSha256']==context['pristineSha256']
             and net_order['prefixBytes']==context['pristineBytes'],
             'NET_ORDER_PRISTINE_PREFIX')
        validate_net_order_promotion(net_order,branches,c['padCount'],c['netCount'])
        net_order_audit=audit_net_order(net_order,pads,branches,arm)
    validate_diagnostic_clones(diagnostic_events,branches,branch_positions,pads,groups,arm)
    diagnostic_clone_steps=sum(row['ownerSteps'] for _,row in diagnostic_events)
    required_hash_bytes=(sum(b['prefixBytes'] for b in branches)
                         +sum(order['prefixBytes'] for order in net_order_rows)
                         +len(pad_bytes(pads))+context['pristineBytes'])
    boundary=result['boundary']
    keys(boundary,('requestCount','outcomeCount','routeRetired','candidateRetired','generatorComplete',
                   'eventObserved','successorAdvanceCalls'),'BOUNDARY_SCHEMA')
    need(boundary==dict(requestCount=1,outcomeCount=1,routeRetired=True,candidateRetired=True,
                       generatorComplete=False,eventObserved=arm!=0,successorAdvanceCalls=0),
         'REQUEST_BOUNDARY')
    need(all(type(boundary[n]) is bool for n in ('routeRetired','candidateRetired','generatorComplete','eventObserved'))
         and all(integer(boundary[n]) for n in ('requestCount','outcomeCount','successorAdvanceCalls')),
         'REQUEST_BOUNDARY_TYPES')
    need(r['attempt']==context['selectedAttempt'] and type(result['outcome']) is str
         and 0<len(result['outcome'].encode())<=2048,'RESULT_OUTCOME')
    physical=result['physical']
    keys(physical,('connectivity','clearanceKeepout','layeredRaster','access','functional'),
         'PHYSICAL_SCHEMA')
    if result['winner'] is None:
        need(result['route']=='TYPED_REQUEST_REJECTION' and r['finalizedOrderings']==0
             and r['selectedOrdering']==-1 and all(x=='NOT_RUN' for x in physical.values()),
             'REJECTED_DISPOSITION')
    else:
        w=result['winner'];keys(w,('geometrySha256','geometryBytes','attempt','ordering','quality'),
                               'WINNER_SCHEMA')
        need(result['route']=='REQUEST_FINALIZED' and digest(w['geometrySha256'])
             and integer(w['geometryBytes'],1,LIMITS['prefixBytes']) and integer(w['attempt'],0,5)
             and w['attempt']==r['attempt'] and integer(w['ordering'],0,2)
             and w['ordering']==r['selectedOrdering'] and r['finalizedOrderings']>0
             and type(w['quality']) is str and 0<len(w['quality'].encode())<=4096
             and all(physical[x]=='PASS' for x in ('connectivity','clearanceKeepout','layeredRaster'))
             and physical['access']==physical['functional']=='NOT_RUN','FINALIZED_DISPOSITION')
        required_hash_bytes+=w['geometryBytes']
    publisher_capture = None
    graph_capture = None
    if arm == 4:
        need(publisher_result is not None and publisher_chunks,
             'PUB_CAPTURE_MISSING')
        payload = b''.join(decoded for _row, _line, decoded in publisher_chunks)
        publisher_capture = _validate_publisher_document(
            payload, [(row, line) for row, line, _decoded in publisher_chunks],
            publisher_result, publisher_result_line, arm, source_sha, h, result,
            branches, net_order, groups, pads)
        checkpoints = publisher_capture['document']['checkpoints']
        terminal_group = checkpoints['publisherTerminal']['group']
        result_group = checkpoints['orderingResult']['group']
        need(all(row['group'] > terminal_group
                 for row, _line, _decoded in publisher_chunks), 'PUB_CHUNK_SOURCE_ORDER')
        need(publisher_result['group'] > result_group, 'PUB_RESULT_SOURCE_ORDER')
        cursor_groups = [group for group in groups if group['graphCursorSteps'] > 0]
        if graph_capture_enabled:
            populated_checkpoints = [value for value in checkpoints.values() if value is not None]
            need(cursor_groups and min(group['ordinal'] for group in cursor_groups)
                 > max(value['group'] for value in populated_checkpoints),
                 'GRAPH_CURSOR_CHECKPOINT_ORDER')
            first_output_group = min(row['group'] for row, _line, _decoded in publisher_chunks)
            need(max(group['ordinal'] for group in cursor_groups) < first_output_group,
                 'GRAPH_CURSOR_BEFORE_OUTPUT')
            need(graph_result is not None and graph_chunks and graph_result_line is not None,
                 'GRAPH_CAPTURE_MISSING')
            graph_payload = b''.join(decoded for _row, _line, decoded in graph_chunks)
            graph_capture = validate_graph_document(
                graph_payload, graph_chunks, graph_result, graph_result_line,
                publisher_capture, h, result, entry, source_sha, branches, net_order,
                groups, pads)
        else:
            need(not graph_chunks and graph_result is None
                 and all(group['graphCursorSteps'] == 0 and group['graphChunkRows'] == 0
                         and group['graphResultRows'] == 0 for group in groups),
                 'GRAPH_DISABLED_RECORD')
        emitted_by_group = {}
        for row, line, decoded in publisher_chunks:
            bucket = emitted_by_group.setdefault(row['group'], [0, 0])
            bucket[0] += len(decoded)
            bucket[1] += len(line)
        emitted_by_group.setdefault(publisher_result['group'], [0, 0])[1] += len(
            publisher_result_line)
        for row, line, decoded in graph_chunks:
            bucket = emitted_by_group.setdefault(row['group'], [0, 0])
            bucket[0] += len(decoded)
            bucket[1] += len(line)
        if graph_result is not None:
            emitted_by_group.setdefault(graph_result['group'], [0, 0])[1] += len(
                graph_result_line)
        for group in groups:
            decoded_bytes, line_bytes = emitted_by_group.get(group['ordinal'], (0, 0))
            need(group['diagnosticSerializedBytes'] == decoded_bytes + line_bytes,
                 'DIAGNOSTIC_OUTPUT_BYTES')
            need(group['diagnosticHashBytes'] >= decoded_bytes + line_bytes
                 and group['diagnosticCopiedEntries'] >= decoded_bytes,
                 'DIAGNOSTIC_GROUP_CAPTURE_WORK')
        expected_serialized = (publisher_capture['payloadBytes']
                               + sum(len(line) for _row, line, _decoded in publisher_chunks)
                               + len(publisher_result_line))
        if graph_capture is not None:
            expected_serialized += graph_capture['payloadBytes'] \
                + sum(len(line) for _row, line, _decoded in graph_chunks) \
                + len(graph_result_line)
        need(sum(group['diagnosticSerializedBytes'] for group in groups)
             == expected_serialized, 'DIAGNOSTIC_SERIALIZED_CENSUS')
        expected_hash_bytes = publisher_capture['captureHashBytes']
        if graph_capture is not None:
            expected_hash_bytes += graph_capture['captureHashBytes']
        need(sum(group['diagnosticHashBytes'] for group in groups)
             == expected_hash_bytes, 'DIAGNOSTIC_HASH_CENSUS')
        path_node_count = len(publisher_capture['document']['path']['nodes'])
        path_copy_floor = path_node_count * 12
        capture_copies = sum(group['diagnosticCopiedEntries'] for group in groups
                             if group['phase'] == 'CAPTURE')
        need(capture_copies >= path_copy_floor, 'DIAGNOSTIC_PATH_COPY_CENSUS')
        if graph_capture is not None:
            need(capture_copies >= path_copy_floor + 32, 'GRAPH_METADATA_COPY_FLOOR')
        found_group = checkpoints['found']['group']
        terminal_group = checkpoints['publisherTerminal']['group']
        before_geometry = publisher_capture['document']['before']
        after_geometry = publisher_capture['document']['after']
        def geometry_copy_entries(snapshot):
            return (sum(7 + len(trace[7]) + len(trace[8])
                        for trace in snapshot['traces']) + 8 * len(snapshot['holes']))
        synchronous_copy_floors = {}
        synchronous_copy_floors[found_group] = (
            len(publisher_capture['document']['branch']['reachedPadIds']) + 24
            + geometry_copy_entries(before_geometry))
        synchronous_copy_floors[terminal_group] = (
            synchronous_copy_floors.get(terminal_group, 0)
            + geometry_copy_entries(after_geometry)
            + utf16_length(geometry_fingerprint(after_geometry['staticPrefix'],
                                               after_geometry['traces'],
                                               after_geometry['holes'])))
        need(all(floor <= LIMITS['snapshotEntries']
                 and groups[group]['diagnosticCopiedEntries'] >= floor
                 for group, floor in synchronous_copy_floors.items()),
             'DIAGNOSTIC_SYNCHRONOUS_SNAPSHOT')
        required_hash_bytes += publisher_capture['captureHashBytes']
        if graph_capture is not None:
            required_hash_bytes += graph_capture['captureHashBytes']
    else:
        need(not publisher_chunks and publisher_result is None and not graph_chunks
             and graph_result is None and not graph_capture_enabled,
             'PUB_NON_CAPTURE_RECORD')
    need(sum(g['hashInputBytes'] for g in groups)>=required_hash_bytes,'HASH_INPUT_CENSUS')
    lw=result['logicalWork'];keys(lw,('generatorAdvances','p07Slices','p07OwnerSteps',
                                     'diagnosticCloneSteps','routingOwnerSteps','expansions'),
                                 'LOGICAL_WORK_SCHEMA')
    raw_owner_steps=sum(g['p07Steps'] or 0 for g in groups)
    measured=dict(generatorAdvances=sum(g['generatorAdvances'] for g in groups),
                  p07Slices=sum(g['p07Slices'] for g in groups),
                  p07OwnerSteps=raw_owner_steps,
                  diagnosticCloneSteps=diagnostic_clone_steps,
                  routingOwnerSteps=raw_owner_steps-diagnostic_clone_steps,
                  expansions=r['expansions'])
    need(lw==measured and all(integer(x) for x in lw.values()) and lw['p07Slices']>0,
         'LOGICAL_WORK_COUNTS')
    need(integer(entry['elapsedToCloseNanos'],1,LIMITS['operationNanos'])
         and integer(entry['maxFullGroupNanos'],1,LIMITS['groupNanos'])
         and entry['maxFullGroupNanos']>=max(g['elapsedBeforeOwnLineNanos'] for g in groups)
         and entry['elapsedToCloseNanos']>=sum(g['elapsedBeforeOwnLineNanos'] for g in groups),
         'FINAL_TIMING_BOUND')
    return dict(header=h,pads=pads,branches=branches,groups=groups,
                diagnosticClones=[row for _,row in diagnostic_events],
                netOrder=net_order,netOrderAudit=net_order_audit,
                result=result,choices=choices,publisherCapture=publisher_capture,
                graphCapture=graph_capture)


def comparable_branch(row):
    return {k:row[k] for k in ('ordinal','group','prefixSha256','prefixBytes','snapshot')}


def comparable_net_order(row):
    if row is None:
        return None
    return {k:v for k,v in row.items() if k not in ('operation','group')}


def comparable_publisher_branch(row):
    # Inline CAPTURE groups advance transport ordinals without advancing P07.
    # Each arm's group binding/accounting is validated before this comparison.
    return {k:v for k,v in row.items() if k not in ('operation','group')}


def validate_study(ops):
    need(len(ops)==5,'FIVE_OPERATIONS')
    def result_parity(o):return {k:o['result'][k] for k in
                                 ('proof','route','context','receipt','outcome','winner','physical')}
    plain=ops[0];default=ops[1];explicit=ops[2]
    need(ops[0]['header']['candidate']==ops[1]['header']['candidate']
         and pad_bytes(ops[0]['pads'])==pad_bytes(ops[1]['pads'])
         and result_parity(plain)==result_parity(default), 'PLAIN_CONTROL_PARITY')
    plain_work=plain['result']['logicalWork']
    for observed in (default,explicit):
        observed_work=observed['result']['logicalWork']
        need(all(observed_work[name]==plain_work[name]
                 for name in ('generatorAdvances','p07Slices','expansions'))
             and observed_work['routingOwnerSteps']==plain_work['routingOwnerSteps']
             and observed_work['p07OwnerSteps']-plain_work['p07OwnerSteps']
                 == observed_work['diagnosticCloneSteps']-plain_work['diagnosticCloneSteps'],
             'PLAIN_CONTROL_PARITY')
    need(ops[1]['header']['candidate']==ops[2]['header']['candidate']
         and pad_bytes(ops[1]['pads'])==pad_bytes(ops[2]['pads'])
         and result_parity(default)==result_parity(explicit)
         and default['result']['logicalWork']==explicit['result']['logicalWork']
         and [comparable_diagnostic_clone(x) for x in default['diagnosticClones']]
         ==[comparable_diagnostic_clone(x) for x in explicit['diagnosticClones']]
         and comparable_net_order(default['netOrder'])==comparable_net_order(explicit['netOrder'])
         and [comparable_branch(x) for x in ops[1]['branches']]
         ==[comparable_branch(x) for x in ops[2]['branches']], 'OBSERVED_CONTROL_PARITY')
    control=ops[2];treatment=ops[3]
    need(control['header']['candidate']==treatment['header']['candidate']
         and pad_bytes(control['pads'])==pad_bytes(treatment['pads'])
         and control['result']['context']==treatment['result']['context'],'TREATMENT_CONTEXT')
    cb=[(i,b) for i,b in enumerate(control['branches']) if b['snapshot']['pass']==0]
    tb=[(i,b) for i,b in enumerate(treatment['branches']) if b['snapshot']['pass']==0]
    need([comparable_branch(b) for _,b in cb]==[comparable_branch(b) for _,b in tb], 'PASS0_PARITY')
    control_events=[comparable_diagnostic_clone(e) for e in control['diagnosticClones']
                    if e['pass']==0]
    treatment_events=[comparable_diagnostic_clone(e) for e in treatment['diagnosticClones']
                      if e['pass']==0]
    need(control_events==treatment_events, 'PASS0_DIAGNOSTIC_CLONE_PARITY')
    captured=ops[4]
    need(treatment['netOrder'] is not None and captured['netOrder'] is not None
         and captured['publisherCapture'] is not None
         and treatment['header']['candidate']==captured['header']['candidate']
         and pad_bytes(treatment['pads'])==pad_bytes(captured['pads'])
         and treatment['result']['context']==captured['result']['context']
         and treatment['result']['proof']==captured['result']['proof']
         and treatment['result']['receipt']==captured['result']['receipt']
         and treatment['result']['route']==captured['result']['route']
         and treatment['result']['outcome']==captured['result']['outcome']
         and treatment['result']['winner']==captured['result']['winner']
         and treatment['result']['physical']==captured['result']['physical']
         and treatment['result']['boundary']==captured['result']['boundary']
         and treatment['result']['logicalWork']==captured['result']['logicalWork']
         and comparable_net_order(treatment['netOrder'])
             == comparable_net_order(captured['netOrder'])
         and [comparable_publisher_branch(row) for row in treatment['branches']]
             == [comparable_publisher_branch(row) for row in captured['branches']]
         and [comparable_diagnostic_clone(row) for row in treatment['diagnosticClones']]
             == [comparable_diagnostic_clone(row) for row in captured['diagnosticClones']],
         'PUBLISHER_CAPTURE_TREATMENT_PARITY')
    capture_document=captured['publisherCapture']['document']
    selected=capture_document['branch']
    next_branch=next((row for row in treatment['branches']
                      if row['snapshot']['pass']==capture_document['context']['pass']
                      and row['ordinal']>selected['ordinal']),None)
    if next_branch is not None:
        need(capture_document['after']['geometrySha256']==next_branch['prefixSha256']
             and capture_document['after']['geometryBytes']==next_branch['prefixBytes'],
             'PUBLISHER_CAPTURE_GEOMETRY_PARITY')
    control_order=control['netOrder'];treatment_order=treatment['netOrder']
    if control_order is None or treatment_order is None:
        return dict(status='NOT_OBSERVED',reason='PASS1_NOT_REACHED')
    need(all(control_order[name]==treatment_order[name] for name in
             ('completedOrderings','yieldedOrderings','promotedNet','prefixSha256','prefixBytes')),
         'TREATMENT_NET_ORDER_CONTEXT')
    control_nets=control_order['netIds'];treatment_nets=treatment_order['netIds']
    for index,(control_net,treatment_net) in enumerate(zip(control_nets,treatment_nets)):
        if control_net!=treatment_net:
            return dict(status='FOUND',firstDifferingIndex=index,
                        controlNetId=control_net,treatmentNetId=treatment_net)
    need(len(control_nets)==len(treatment_nets),'TREATMENT_NET_ORDER_LENGTH')
    return dict(status='NO_DIVERGENCE',netCount=len(control_nets))


def check_source_manifest(raw, base):
    manifest=strict_json(raw)
    keys(manifest,('schema','kind','parentHead','repositoryRoot','baseManifest','files'),
         'SOURCE_MANIFEST_SCHEMA')
    need(manifest['schema']=='tsj-branch-order-sources-v1' and manifest['kind'] in KINDS
         and type(manifest['parentHead']) is str
         and re.fullmatch('[0-9a-f]{40}',manifest['parentHead']) is not None,
         'SOURCE_MANIFEST_IDENTITY')
    root=Path(manifest['repositoryRoot']);need(root.is_absolute(),'SOURCE_ROOT')
    no_reparse(root)
    need(root.resolve()==Path(__file__).resolve().parents[4], 'SOURCE_REPOSITORY_IDENTITY')
    files=manifest['files'];need(type(files) is list and 2<=len(files)<=4096,'SOURCE_FILE_COUNT')
    seen=set();roles=[];pinned=[]
    for row in files:
        keys(row,('role','path','bytes','sha256'),'SOURCE_FILE_SCHEMA')
        need(row['role'] in ('BASE_SOURCE','WRITER','HOST','READER','WIRE','TEST')
             and type(row['path']) is str and not Path(row['path']).is_absolute()
             and '\\' not in row['path'] and ':' not in row['path']
             and all(x not in ('','.','..') for x in row['path'].split('/'))
             and row['path'].casefold() not in seen,'SOURCE_FILE_PATH_ROLE')
        seen.add(row['path'].casefold());roles.append(row['role'])
        if row['role']=='READER':
            need(row['path']=='tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py',
                 'READER_SOURCE_PATH')
        elif row['role']=='WIRE':
            need(row['path']=='docs/task-evidence/Q100/outf-graph-r1/WIRE.md','WIRE_SOURCE_PATH')
        elif row['role']=='WRITER':
            need(row['path'].startswith('tests/qualification/rb100-layout/java/')
                 and row['path'].endswith('.java'),'WRITER_SOURCE_PATH')
        elif row['role']=='HOST':
            need(row['path']=='scripts/verify-rb100-branch-order-study.ps1','HOST_SOURCE_PATH')
        elif row['role']=='TEST':
            need(row['path'].startswith('tests/qualification/rb100-layout/branch-tree-study/')
                 and row['path'].endswith('.py'),'TEST_SOURCE_PATH')
    need(roles.count('READER')==roles.count('WIRE')==1,'SOURCE_ROLES')
    wire=next(row['sha256'] for row in files if row['role']=='WIRE')
    if manifest['kind']=='READER_FIXTURE':
        need(manifest['baseManifest'] is None and 'BASE_SOURCE' not in roles,'FIXTURE_SOURCE_SCOPE')
    else:
        need(roles.count('BASE_SOURCE')==BASE_ROWS and roles.count('WRITER')==1
             and roles.count('HOST')==1 and roles.count('READER')==1
             and roles.count('WIRE')==1 and len(files)==BASE_ROWS+4,
             'NATIVE_SOURCE_CLOSURE')
        keys(manifest['baseManifest'],('path','bytes','sha256'),'BASE_MANIFEST_REF')
        need(manifest['baseManifest']['sha256']==BASE_SHA
             and manifest['baseManifest']['bytes']==BASE_MANIFEST_BYTES
             and type(manifest['baseManifest']['path']) is str
             and Path(manifest['baseManifest']['path']).name=='inputs.json','BASE_MANIFEST_DECLARATION')
        b,p=file_ref(manifest['baseManifest'],base,4*1024*1024)
        need(sha(b)==BASE_SHA and len(b)==BASE_MANIFEST_BYTES,'BASE_MANIFEST_PIN')
        pins=strict_json(b);need(type(pins) is list and len(pins)==BASE_ROWS,'BASE_MANIFEST_CENSUS')
        actual=[{k:r[k] for k in ('path','bytes','sha256')} for r in files if r['role']=='BASE_SOURCE']
        need(actual==pins and sum(r['bytes'] for r in actual)==BASE_SOURCE_BYTES,'BASE_SOURCE_ROWS')
        pinned.append((manifest['baseManifest'],base,4*1024*1024))
    # Close the complete role/path/base census before reading any source body.
    for row in files:
        ref={k:row[k] for k in ('path','bytes','sha256')}
        _,p=file_ref(ref,root,100*1024*1024)
        pinned.append((ref,root,100*1024*1024))
        if row['role']=='READER':need(p.resolve()==Path(__file__).resolve(),'READER_SOURCE_IDENTITY')
    return manifest,wire,pinned


def validate_spec(path, expected_sha):
    need(digest(expected_sha),'SPEC_DIGEST_ARGUMENT')
    original=stable_read(path,4*1024*1024);need(sha(original)==expected_sha,'SPEC_DIGEST')
    spec=strict_json(original);keys(spec,('schema','kind','sourceManifest','studyIndex','transport'),
                                  'SPEC_SCHEMA')
    need(spec['schema']=='tsj-branch-order-reader-input-v1' and spec['kind'] in KINDS,'SPEC_KIND')
    base=Path(path).absolute().parent
    source,sp=file_ref(spec['sourceManifest'],base,4*1024*1024)
    manifest,wire,pins=check_source_manifest(source,sp.parent)
    need(manifest['kind']==spec['kind'],'SOURCE_KIND')
    index_raw,ip=file_ref(spec['studyIndex'],base,65536);index=strict_json(index_raw)
    keys(index,('schema','runId','kind','sourceManifestSha256','wireSha256','operations'),
         'INDEX_SCHEMA')
    need(index['schema']=='tsj-branch-order-index-v1' and index['kind']==spec['kind']
         and type(index['runId']) is str
         and re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',index['runId'])
         and index['sourceManifestSha256']==sha(source) and index['wireSha256']==wire
         and type(index['operations']) is list and len(index['operations'])==5,'INDEX_IDENTITY')
    ops=[];cleanup=0;total_bytes=0;extra_pins=[]
    for arm,entry in enumerate(index['operations']):
        keys(entry,('operation','armId','receipt','elapsedToCloseNanos','maxFullGroupNanos','cleanup'),
             'INDEX_OPERATION_SCHEMA')
        keys(entry['receipt'],('path','bytes','sha256'),'INDEX_RECEIPT_SCHEMA')
        need(integer(entry['operation'],0,4) and entry['operation']==arm and entry['armId']==ARMS[arm][0]
             and entry['receipt']['path']=='op-'+str(arm)+'.jsonl','INDEX_OPERATION_ORDER')
        data,p=file_ref(entry['receipt'],ip.parent,LIMITS['receiptBytes'],relative=True)
        extra_pins.append((entry['receipt'],ip.parent,LIMITS['receiptBytes']))
        total_bytes+=len(data);need(total_bytes<=32*1024*1024,'STUDY_BYTE_CAP')
        c=entry['cleanup'];keys(c,('status','elapsedNanos','ownersReleased','contextRestored'),
                              'CLEANUP_SCHEMA')
        need(c['status']=='PASS' and integer(c['elapsedNanos'],0,120_000_000_000)
             and c['ownersReleased'] is True and c['contextRestored'] is True,'CLEANUP_PROOF')
        cleanup+=c['elapsedNanos']
        ops.append(validate_operation(data,entry,sha(source),wire,arm,index['runId'],spec['kind']))
    need(cleanup<=120_000_000_000,'CUMULATIVE_CLEANUP_LIMIT')
    net_order_comparison=validate_study(ops)
    if spec['kind']=='READER_FIXTURE':
        need(spec['transport'] is None,'FIXTURE_TRANSPORT')
        status='PASS_READER_FIXTURE_ONLY'
    else:
        tr,tp=file_ref(spec['transport'],base,1024*1024);t=strict_json(tr)
        keys(t,('schema','kind','sourceManifestSha256','indexSha256','compileExitCode','exitCode',
                'terminationProven','sourcesStable','processId','processStartTicks',
                'executableSha256','compilerExecutableSha256','powershellExecutableSha256',
                'isolationModuleSha256','compilerTerminationProven',
                'nativeElapsedNanos','cleanupElapsedNanos','cleanupStatus'),
             'TRANSPORT_SCHEMA')
        need(t['schema']=='tsj-branch-order-native-transport-v1' and t['kind']==spec['kind']
             and t['sourceManifestSha256']==sha(source) and t['indexSha256']==sha(index_raw)
             and type(t['compileExitCode']) is int and t['compileExitCode']==0
             and type(t['exitCode']) is int and t['exitCode']==0
             and t['terminationProven'] is True and t['sourcesStable'] is True
             and integer(t['processId'],1) and type(t['processStartTicks']) is str
             and re.fullmatch('[1-9][0-9]*',t['processStartTicks'])
             and t['executableSha256']=='e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
             and t['compilerExecutableSha256']=='61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
             and t['powershellExecutableSha256']=='60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f'
             and t['isolationModuleSha256']=='fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
             and t['compilerTerminationProven'] is True
             and integer(t['nativeElapsedNanos'],1,480_000_000_000)
             and integer(t['cleanupElapsedNanos'],cleanup,120_000_000_000)
             and t['cleanupStatus']=='PASS','NATIVE_TRANSPORT_PROOF')
        need(t['nativeElapsedNanos']>=sum(x['elapsedToCloseNanos'] for x in index['operations'])
             +t['cleanupElapsedNanos'],
             'NATIVE_TIME_CENSUS')
        extra_pins.append((spec['transport'],base,1024*1024))
        status='VALIDATED_NATIVE_REPORT_ONLY_ROOT_ACCEPTANCE_REQUIRED'
    for ref,root,maximum in pins+extra_pins+[(spec['sourceManifest'],base,4*1024*1024),
                                          (spec['studyIndex'],base,65536)]:
        file_ref(ref,root,maximum)
    need(stable_read(path,4*1024*1024)==original,'SPEC_CHANGED')
    graph_diagnostics=[]
    for operation in ops:
        graph=operation['graphCapture']
        if graph is None:
            graph_diagnostics.append(None)
            continue
        document=graph['document']
        publisher=operation['publisherCapture']
        graph_diagnostics.append(dict(
            targetId=GRAPH_TARGET_ID,
            publisherPayloadSha256=publisher['payloadSha256'],
            graphPayloadSha256=graph['payloadSha256'],
            prefixGeometrySha256=document['body']['stability']['prefixGeometrySha256'],
            pathTupleSha256=document['body']['path']['tupleSha256'],
            goalFacesSha256=document['goalFacesSha256'],
            faces=graph['diagnostics'],
            scope='ORDINARY_EDGE_LENGTH_ONLY'))
    return dict(schema='tsj-branch-order-reader-result-v1',status=status,kind=spec['kind'],
                specSha256=expected_sha,sourceManifestSha256=sha(source),indexSha256=sha(index_raw),
                operations=5,branches=[len(o['branches']) for o in ops],
                netOrderComparison=net_order_comparison,
                netOrderAudits=[o['netOrderAudit'] for o in ops],
                routeOutcomes=[o['result']['route'] for o in ops],
                publisherOutcomes=[None if o['publisherCapture'] is None else
                                   o['publisherCapture']['document']['publisher']['outcome']
                                   for o in ops],
                graphDiagnostics=graph_diagnostics,
                limitations=dict(rootNativeAcceptance='NOT_GRANTED_BY_READER',
                                 tailFoundPublication='UNKNOWN_WITHOUT_LATER_SELECTION_OR_YIELD',
                                 gridOnlyOrdering='UNSUPPORTED_MISSING_BRANCH_PROOF',
                                 layoutQualification=False,defaultAdoption=False,playable100=0,beta='HELD'))


def main(argv=None):
    p=argparse.ArgumentParser();p.add_argument('--spec',required=True)
    p.add_argument('--spec-sha256',required=True);p.add_argument('--output',required=True)
    a=p.parse_args(argv);result=validate_spec(a.spec,a.spec_sha256)
    out=Path(a.output).absolute();no_reparse(out.parent)
    with out.open('xb') as stream:
        stream.write((json.dumps(result,sort_keys=True,indent=2,allow_nan=False)+'\n').encode())


if __name__=='__main__': main()
