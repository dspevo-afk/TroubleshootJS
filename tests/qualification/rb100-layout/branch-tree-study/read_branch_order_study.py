"""Strict, read-only independent branch-choice evidence consumer.

Fixture acceptance certifies this reader only. Native reports still require root
review of the actual compiler, process, context and cleanup evidence.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat

SCHEMA = 'p07-actual-branch-order-study-v4'
KINDS = ('READER_FIXTURE', 'TINY_NATIVE', 'AUTHORED')
ARMS = (
    ('PLAIN_ENTRY_RECEIPT_CONTROL', 'PLAIN_BEGIN', 'CANONICAL_NEAREST', 'CANONICAL', False),
    ('OBSERVED_DEFAULT_NEAREST', 'BOUNDARY_CONVENIENCE', 'CANONICAL_NEAREST', 'CANONICAL', True),
    ('OBSERVED_EXPLICIT_NEAREST', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST', 'CANONICAL', True),
    ('SECOND_ORDER_MINIMUM_PAIR', 'BOUNDARY_EXPLICIT', 'CANONICAL_NEAREST',
     'SECOND_ORDER_MINIMUM_PAIR', True),
)
LIMITS = dict(operationNanos=90_000_000_000, groups=640, groupNanos=5_000_000_000,
              p07Steps=32768, receiptBytes=8*1024*1024, lineBytes=65536,
              padRows=1024, prefixBytes=1024*1024, viaCost=60, holeCap=48,
              transitionCap=4, orderings=3, requestExpansions=1_000_000,
              branchExpansions=100_000, cells=250_000)
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
                'callbacks censusRows netOrderRows hashInputBytes serializedBeforeOwnLineBytes').split()
NET_ORDER_FIELDS = ('record operation group pass strategy promotedNet completedOrderings '
                    'yieldedOrderings prefixSha256 prefixBytes netIds').split()
DIAGNOSTIC_CLONE_FIELDS = ('record operation ordinal group pass netId startPadId rootPadId '
                           'faceIndex prefixViaCount ownerSteps').split()
DIAGNOSTIC_BOUND_PROOFS = ('relaxedBoundObservationChecked relaxedBoundLiveLayoutStable '
                            'relaxedBoundRouterStateStable relaxedBoundRouterEffectsStable').split()
BASE_SHA = '92e4a3ba69de6a8666f9789e7facafb5d2a6d8461f42c0f2ccca8e95f7141e7b'
BASE_ROWS = 1080
BASE_MANIFEST_BYTES = 228856
BASE_SOURCE_BYTES = 87535768
TOKEN = re.compile(r'[-+A-Za-z0-9_.:]{1,128}\Z')
HEX = re.compile(r'[0-9a-f]{64}\Z')


def need(condition, code):
    if not condition:
        raise ValueError(code)


def keys(value, names, code):
    need(type(value) is dict and set(value) == set(names), code)


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
        need(re.fullmatch(r'op-[0-3]\.jsonl', ref['path']) is not None, 'RECEIPT_PATH')
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
         and integer(h['operation'],0,3) and h['operation']==arm and h['sourceManifestSha256']==source_sha
         and h['wireSha256']==wire_sha and h['limits']==LIMITS, 'HEADER_IDENTITY')
    keys(h['arm'],('id','api','strategy','netOrderStrategy','observed'),'ARM_SCHEMA')
    need(tuple(h['arm'][x] for x in ('id','api','strategy','netOrderStrategy','observed'))==ARMS[arm]
         and type(h['arm']['observed']) is bool, 'ARM_IDENTITY')
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
    else: need(h['mode']=='TINY_CANARY' and c['variant']=='TINY', 'TINY_CANDIDATE')
    pads=[]; branches=[]; groups=[]; result=None; current_group=0; group_bytes=0
    group_callbacks=0; group_pads=0; group_diagnostic_clones=0; group_net_orders=0
    branch_positions=[]; diagnostic_events=[]; net_order_rows=[]; net_order_positions=[]
    branch_seen=False; report_seen=False; report_closed=False
    pass_one_seen=False
    for line_index,(row,line) in enumerate(zip(rows[:-1],lines[:-1])):
        need(not report_closed, 'TRAILING_REPORT_RECORD')
        need(type(row) is dict and type(row.get('record')) is str, 'RECORD_TYPE')
        typ=row['record']
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
            need(not report_seen and arm!=0 and integer(row['operation'],0,3) and row['operation']==arm
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
            need(not report_seen and arm in (1,2,3) and not pass_one_seen
                 and len(pads)==c['padCount'], 'NET_ORDER_POSITION')
            validate_pad_census(pads,c)
            need(integer(row['operation'],1,3) and row['operation']==arm
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
        elif typ=='DIAGNOSTIC_CLONE':
            keys(row,DIAGNOSTIC_CLONE_FIELDS,'DIAGNOSTIC_CLONE_SCHEMA')
            need(arm in (1,2,3) and integer(row['operation'],1,3) and row['operation']==arm
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
            need(result is None and integer(row['operation'],0,3) and row['operation']==arm
                 and row['proof']=='COMPLETE', 'RESULT_IDENTITY')
            result=row;report_seen=True
        elif typ=='GROUP':
            keys(row,GROUP_FIELDS,'GROUP_SCHEMA')
            need(integer(row['operation'],0,3) and row['operation']==arm
                 and integer(row['ordinal'],0,639) and row['ordinal']==current_group
                 and row['phase'] in ('SETUP','ADVANCE','PHYSICAL','REPORT'), 'GROUP_IDENTITY')
            for n in ('elapsedBeforeOwnLineNanos','observerChecks','callbacks','censusRows',
                      'netOrderRows','hashInputBytes','serializedBeforeOwnLineBytes',
                      'diagnosticCloneSteps'):
                need(integer(row[n]),'GROUP_NUMBER')
            need(row['elapsedBeforeOwnLineNanos']<=LIMITS['groupNanos']
                 and integer(row['generatorAdvances'],0,1) and integer(row['p07Slices'],0,1)
                 and row['p07Slices']<=row['generatorAdvances'], 'GROUP_LIMIT')
            need(row['generatorAdvances']==(1 if row['phase']=='ADVANCE' else 0), 'GROUP_PHASE_WORK')
            if row['p07Slices']:
                need(row['accounting']=='MEASURED' and integer(row['p07Steps'],0,32768)
                     and row['observerChecks']>=1,'P07_ACCOUNTING')
            else:need(row['accounting']=='NOT_APPLICABLE' and row['p07Steps'] is None,'P07_ABSENCE')
            need(row['callbacks']==group_callbacks and row['censusRows']==group_pads
                 and row['netOrderRows']==group_net_orders
                 and row['serializedBeforeOwnLineBytes']==group_bytes, 'GROUP_RECORD_COUNTS')
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
                 if order['group']==current_group), 'GROUP_HASH_BYTES')
            if report_seen:
                need(row['phase']=='REPORT','FINAL_GROUP_PHASE')
                report_closed=True
            groups.append(row);current_group+=1;group_bytes=0;group_callbacks=0;group_pads=0
            group_diagnostic_clones=0;group_net_orders=0
            need(current_group<=640,'GROUP_COUNT');continue
        else:raise ValueError('UNEXPECTED_RECORD')
        need(integer(row.get('group'),0,639) and row['group']==current_group,'RECORD_GROUP')
        group_bytes+=len(line)
    f=rows[-1]
    keys(f,('record','group','operation','complete','padCount','branchCount','groupCount'),'FOOTER_SCHEMA')
    need(result is not None and groups and groups[-1]['phase']=='REPORT'
         and group_bytes==0 and f['record']=='FOOTER' and integer(f['operation'],0,3) and f['operation']==arm
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
                result=result,choices=choices)


def comparable_branch(row):
    return {k:row[k] for k in ('ordinal','prefixSha256','prefixBytes','snapshot')}


def comparable_net_order(row):
    if row is None:
        return None
    return {k:v for k,v in row.items() if k not in ('operation','group')}


def validate_study(ops):
    need(len(ops)==4,'FOUR_OPERATIONS')
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
            need(row['path']=='docs/task-evidence/Q100/net-priority-r1/WIRE.md','WIRE_SOURCE_PATH')
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
         and type(index['operations']) is list and len(index['operations'])==4,'INDEX_IDENTITY')
    ops=[];cleanup=0;total_bytes=0;extra_pins=[]
    for arm,entry in enumerate(index['operations']):
        keys(entry,('operation','armId','receipt','elapsedToCloseNanos','maxFullGroupNanos','cleanup'),
             'INDEX_OPERATION_SCHEMA')
        keys(entry['receipt'],('path','bytes','sha256'),'INDEX_RECEIPT_SCHEMA')
        need(integer(entry['operation'],0,3) and entry['operation']==arm and entry['armId']==ARMS[arm][0]
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
    return dict(schema='tsj-branch-order-reader-result-v1',status=status,kind=spec['kind'],
                specSha256=expected_sha,sourceManifestSha256=sha(source),indexSha256=sha(index_raw),
                operations=4,branches=[len(o['branches']) for o in ops],
                netOrderComparison=net_order_comparison,
                netOrderAudits=[o['netOrderAudit'] for o in ops],
                routeOutcomes=[o['result']['route'] for o in ops],
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
