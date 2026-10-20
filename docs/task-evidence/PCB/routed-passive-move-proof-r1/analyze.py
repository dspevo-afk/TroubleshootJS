"""Explain the actual courtyard rejection; never convert the failed proof to PASS."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
EXPECTED_ERROR = ('java.lang.IllegalStateException: PCB trace passes through component '
    'routing courtyard: CTRL_RETURN / C5 segment 3256,1094 -> 3226,1094 '
    'keepOut=Rect(3171,1094,80,90) startPad=U1.RETURN endPad=C5.2')

def result():
    protocol = read(HERE/'protocol.json')
    unit = read(HERE/'unit-timing.json')
    timing = read(HERE/'routes-timing.json')
    text = (HERE/'routes.txt').read_text(encoding='utf-8-sig')
    assert unit['status'] == 'PASS' and unit['exitCode'] == 0
    assert timing['status'] == 'FAIL' and timing['exitCode'] == 2
    assert text.count('Exception in thread "main" '+EXPECTED_ERROR) == 1
    assert text.count('CURRENT_CONTRACT_FAILURE:') == 1
    assert text.count('CLEANUP: current JVM contract classes and task-owned scratch removed.') == 1
    assert 'PcbBoardLayout.validateTraceCourtyards(' in text
    assert 'PcbRoutedPassiveMoveProofContractTest.constructCandidate(' in text
    assert not (HERE/'assessment.json').exists()
    assert not (HERE/'routes-receipt.txt').exists()
    prefix = protocol['proofPrefix']
    rows = [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]
    assert len(rows) == 1 and rows[0]['seed'] == '5' and rows[0]['status'] == 'NO_TARGET'
    assert rows[0]['constructionCount'] == 0 and rows[0]['selectedMutation'] is False
    old = HERE.parent/'routed-passive-leg-census-r1/routes.txt'
    census_prefix = 'PCB_ROUTED_LEG_CENSUS '
    assert protocol['reportOrder'][1] == census_prefix
    summaries = [json.loads(line[len(census_prefix):])
        for line in old.read_text().splitlines() if line.startswith(census_prefix)]
    target = next(c for r in summaries if r['seed'] == '2'
        for c in r['successfulCallbacks'] if c['target'])
    moves = [(p['componentId'], d['dx'], d['dy'], d['copperDelta'])
        for p in target['parts'] if p['eligiblePackage'] for d in p['directions']
        if d['eligible'] and d['copperDelta'] <= 0]
    assert moves[0] == ('C5', 0, -10, -10)
    c5 = next(p for p in target['parts'] if p['componentId'] == 'C5')
    # Independent literal endpoint arithmetic from the sealed census.
    deltas = []
    for pad in c5['pads']:
        for incident in pad['incidents']:
            old_length = abs(pad['x']-incident['neighborX'])+abs(pad['y']-incident['neighborY'])
            new_length = abs(pad['x']-incident['neighborX'])+abs(pad['y']-10-incident['neighborY'])
            deltas.append(new_length-old_length)
    assert deltas == [-10, 10, -10] and sum(deltas) == -10
    # The unchanged interior segment overlaps the moved courtyard's top edge by25.
    x1, x2, y = 3256, 3226, 1094
    left, top, width, height = 3171, 1094, 80, 90
    overlap = min(max(x1,x2),left+width)-max(min(x1,x2),left)
    assert overlap == 25 and top <= y <= top+height and y < top+10
    return {'analysisStatus':'COMPLETE', 'nativeProofStatus':'FAIL',
        'scope':'One first-canonical private candidate; no substitute move or admission',
        'protocolSha256':sha(HERE/'protocol.json'), 'routesLogSha256':sha(HERE/'routes.txt'),
        'unit':unit, 'routes':timing, 'expectedMoveFromPublishedCensus':protocol['expectedMove'],
        'endpointAnalyticDeltas':deltas, 'endpointAnalyticTotal':-10,
        'physicalRejection':{'validator':'PcbBoardLayout.validateTraceCourtyards',
            'componentId':'C5', 'netId':'CTRL_RETURN',
            'unchangedInteriorSegment':[[3256,1094],[3226,1094]],
            'movedCourtyard':[3171,1094,80,90], 'literalClosedEdgeOverlap':overlap,
            'exactException':EXPECTED_ERROR},
        'completeMoveRows':rows, 'completeSelectedRows':1,
        'seed2FinalSelectedFidelity':'NOT_COMPLETED_AFTER_PROPAGATED_FAILURE',
        'sourceAfterHashAndCandidateReachability':'NOT_REACHED',
        'explicitTwoLayerValidation':'NOT_REACHED', 'actualCopperTotal':'NOT_REACHED',
        'boardShrinkage':'NOT_DEMONSTRATED', 'policyChange':'NONE',
        'limits':protocol['limits']}

if __name__ == '__main__':
    value = result()
    target = HERE/'move-analysis.json'
    if target.exists():
        assert read(target) == value, 'Existing analysis differs; preserve it'
    else:
        target.write_text(json.dumps(value, indent=2)+'\n')
    print('COMPLETE rejection analysis; native proof FAIL, courtyard collision; no admission')
