"""Validate and seal baseline receipts without displaying their measurements."""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
SEEDS = {'2','3','4','6'}
DIGEST = re.compile(r'^[0-9a-f]{64}$')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def signed_seed(value):
    return (isinstance(value,str) and re.fullmatch(r'-?(0|[1-9][0-9]*)',value) is not None
            and str(int(value))==value and -(1<<63)<=int(value)<(1<<63))


def main():
    timing = json.loads((HERE/'native-timing.json').read_text(encoding='utf-8-sig'))
    audit = json.loads((HERE/'post-native-input-audit.json').read_text(encoding='utf-8-sig'))
    provenance = json.loads((HERE/'baseline-provenance.json').read_text(encoding='utf-8-sig'))
    require(timing['status']=='PASS' and audit['status']=='PASS', 'Native terminal/input gates are not PASS')
    require(provenance['SourceDigest']=='b5c9c975207e19659fcdc2c7b8def06fe8a282ecec3d53c2e456556522101ee1',
            'Baseline production source is not accepted13 bytes')
    raw = (HERE/'native.txt').read_bytes()
    text = raw.decode('utf-8-sig')
    require('PASS: PCB fresh qualification baseline contracts ' in text and
            'CLEANUP: current JVM contract classes and task-owned scratch removed.' in text,
            'Missing baseline/cleanup terminal receipts')
    cases, voids = {}, {}
    for line in text.splitlines():
        if line.startswith('PCB_FRESH_BASELINE_CASE '):
            case = json.loads(line.split(' ',1)[1])
            require(case['seed'] not in cases, 'Duplicate baseline seed')
            cases[case['seed']] = case
        elif line.startswith('PCB_WHITESPACE_CASE '):
            case = json.loads(line.split(' ',1)[1])
            require(case['seed'] not in voids, 'Duplicate whitespace seed')
            voids[case['seed']] = case
    require(set(cases)==SEEDS and set(voids)==SEEDS, 'Predeclared cohort missing or changed')
    integer_fields = ('version','density','parts','pads','area','width','height',
                      'largestProtectedEmptyRectangleArea','copperManhattanLength','vias',
                      'selectedAttempt','acceptedPlanEvaluations','placementRejections',
                      'oneFaceAttempts','twoLayerAttempts','routeAttempts','elapsedMillis')
    for seed, case in cases.items():
        require(case.get('status')=='PASS', 'Non-PASS physical case for seed '+seed)
        require(all(type(case[k]) is int and case[k]>=0 for k in integer_fields),
                'Invalid integer field for seed '+seed)
        require(type(voids[seed]['version']) is int and
                type(voids[seed]['largestProtectedEmptyRectangle']['area']) is int,
                'Invalid whitespace integer field for seed '+seed)
        require(case['version']==13 and case['density']==12 and voids[seed]['version']==13,
                'Wrong baseline interpretation for seed '+seed)
        require(signed_seed(case['layoutSeed']) and signed_seed(case['routingSeed']),
                'Noncanonical signed-long transport for seed '+seed)
        require(40<=case['parts']<=60 and case['pads']>=case['parts'], 'Invalid census for seed '+seed)
        require(case['width']>0 and case['height']>0 and
                case['area']==case['width']*case['height']<=64000000, 'Invalid outline for seed '+seed)
        require(case['largestProtectedEmptyRectangleArea']==voids[seed]['largestProtectedEmptyRectangle']['area']
                <=case['area'], 'Whitespace receipt mismatch for seed '+seed)
        require(DIGEST.fullmatch(case['geometrySha256']) and DIGEST.fullmatch(case['sourceGraphSha256']),
                'Invalid identity digest for seed '+seed)
        rank = case['rankedPlacementAttempts']
        require(isinstance(rank,list) and 1<=len(rank)<=6 and len(set(rank))==len(rank) and
                all(type(x) is int and 0<=x<6 for x in rank) and case['selectedAttempt'] in rank,
                'Invalid rank/selected identity for seed '+seed)
        require(0<case['acceptedPlanEvaluations']<=360000 and case['placementRejections']<=6 and
                case['oneFaceAttempts']<=3 and 0<case['twoLayerAttempts']<=3 and
                case['routeAttempts']==case['oneFaceAttempts']+case['twoLayerAttempts']<=6,
                'Counter/cap boundary mismatch for seed '+seed)
    sealed = dict(status='SEALED_ACCEPTED13_BASELINES',sourceCommit='22df5e0d0c58de516f40ccc59fe71f1a33fb9305',
                  sourceLogSha256=hashlib.sha256(raw).hexdigest(),
                  nativeInputsSha256=hashlib.sha256((HERE/'native-inputs.json').read_bytes()).hexdigest(),
                  cases=[cases[seed] for seed in ('2','3','4','6')],
                  counterScope='acceptedPlanEvaluations sums accepted raw plans only; discarded partial work excluded',
                  limits='Four native PCB cases only; no solver/customer, allseed or manufacturing qualification')
    payload = (json.dumps(sealed,indent=2)+'\n').encode('utf-8')
    receipt = dict(status='PASS',seeds=['2','3','4','6'],physicalCasesAccepted=4,
                   baselineSha256=hashlib.sha256(payload).hexdigest(),
                   sourceLogSha256=sealed['sourceLogSha256'],
                   schema='All identities, types, census, outline/void consistency and native caps checked without printing measurements',
                   blinding='Values remain uninspected by root/workers until a frozen proposal passes existing regressions; workflow boundary only')
    receipt_payload = (json.dumps(receipt,indent=2)+'\n').encode('utf-8')
    outputs = [(HERE/'sealed-baselines.json',payload),(HERE/'sealed-receipt.json',receipt_payload)]
    for path, content in outputs:
        require(not path.exists() or path.read_bytes()==content,
                'Existing frozen output differs; refusing baseline replacement')
    for path, content in outputs:
        if not path.exists():
            path.write_bytes(content)
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
