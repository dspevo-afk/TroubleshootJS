"""Independent finite measurement reader; a native PASS is not a packing gain."""
import hashlib
import json
import math
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent


def require(value, message):
    if not value:
        raise ValueError(message)


def integer(value, lower, upper, label):
    require(type(value) is int and lower <= value <= upper, label)


def digest(value):
    require(isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value), 'Invalid digest')


def assess(rows, accepted):
    baseline = {r['seed']: r for r in accepted}
    require(set(baseline) == {'42', '-1022'}, 'Unexpected baseline roots')
    require(len(rows) == 2 and {r['seed'] for r in rows} == set(baseline), 'Missing/duplicate roots')
    outcomes = []
    for row in rows:
        seed = row['seed']
        for key in baseline[seed]:
            if key == 'elapsedMillis':
                continue
            require(row[key] == baseline[seed][key], 'Accepted export changed: ' + seed + '/' + key)
        require(row['status'] == 'PASS' and row['version'] == 15 and row['density'] == 14,
                'Unexpected interpretation/status')
        require(row['selectedAttempt'] == (1 if seed == '42' else 0), 'Selected attempt')
        d = row['diagnostic']
        require(d['coordinateFrame'] == 'RAW_PRE_PLACEMENT_COMPACTION', 'Mixed coordinate frame')
        require(type(d['traceSeed']) is int and d['traceSeed'] == int(row['layoutSeed']) and
                type(d['seed']) is int and d['seed'] == int(seed) and
                d['selectedAttempt'] == row['selectedAttempt'] and d['density'] == 14,
                'Diagnostic seed/attempt/interpretation binding')
        require(d['bindingStatus'] in ('BOUND', 'INCONCLUSIVE'), 'Unknown binding state')
        trace = d['trace']
        require(trace['cap'] == 60000 and type(trace['capped']) is bool, 'Trace contract')
        integer(trace['records'], 0, 60000, 'Trace count')
        integer(trace['chosenPlacements'], 0, row['parts'], 'Choice count')
        for key in ('rawPlanSha256', 'returnedPlanSha256', 'selectedPlacementPlanSha256'):
            digest(d[key])
        require(d['selectedPlacementPlanSha256'] == row['selectedPlacementPlanSha256'], 'Selected binding')
        require(type(d['returnedPlanMatchesSelected']) is bool, 'Binding bool')
        require(d['returnedPlanMatchesSelected'] ==
                (d['returnedPlanSha256'] == d['selectedPlacementPlanSha256']), 'False returned binding')
        require(d['rawPlanFingerprintMatchesSelected'] ==
                (d['rawPlanSha256'] == d['selectedPlacementPlanSha256']), 'False raw binding')
        comparisons = d['chosenComparisons']
        require(len({c['componentId'] for c in comparisons}) == len(comparisons), 'Duplicate comparisons')
        for c in comparisons:
            require(c['coordinatesMatch'] == (c['rawX'] == c['recordedX'] and c['rawY'] == c['recordedY']),
                    'Chosen coordinate comparison')
            require(c['poseMatches'] == (c['rawRotation'] == c['recordedRotation'] and
                    c['rawMountingSide'] == c['recordedMountingSide']), 'Chosen pose comparison')
            require(c['comparable'] == (c['choiceFound'] and c['coordinatesMatch'] and
                    c['poseMatches'] and c['chosenScore'] is not None and math.isfinite(c['chosenScore'])),
                    'Chosen comparability')
        rectangle = d['rawAccessEnvelopeRectangle']
        require(rectangle['grid'] == 10 and rectangle['occupancy'] == 'UNION_OF_FULL_ACCESS_ENVELOPES',
                'Wrong raw raster interpretation')
        integer(rectangle['cells'], 0, 1000000, 'Raster cap')
        require(rectangle['cells'] == rectangle['rasterCells'], 'Raster allocation alias')
        require(rectangle['domainPolicy'] == 'FIXED_RAW_ENVELOPE_BOUNDS_CLIPPED', 'Moving domain')
        if rectangle['status'] == 'PASS':
            integer(rectangle['area'], 0, 10**12, 'Raw area')
            require(rectangle['area'] == rectangle['width'] * rectangle['height'], 'Raw rectangle area')
            require(rectangle['emptyRectangleCells'] * 100 == rectangle['area'], 'Empty cell count')
            require(rectangle['domainCellCount'] * 100 == rectangle['domainWidth'] * rectangle['domainHeight']
                    and rectangle['domainCellCount'] <= rectangle['rasterCells'], 'Domain allocation')
            require(rectangle['domainX'] <= rectangle['x'] and rectangle['domainY'] <= rectangle['y'] and
                    rectangle['x'] + rectangle['width'] <= rectangle['domainX'] + rectangle['domainWidth'] and
                    rectangle['y'] + rectangle['height'] <= rectangle['domainY'] + rectangle['domainHeight'],
                    'Rectangle outside fixed domain')
        sample = d['sample']
        require(sample['componentLimit'] == 4 and sample['originsPerComponentLimit'] == 32 and
                sample['wholePlanValidationLimit'] == 128 and sample['rectangleRecomputeLimit'] == 16,
                'Changed declared sample')
        integer(sample['wholePlanValidations'], 0, 128, 'Validation count')
        integer(sample['rectangleRecomputations'], 0, sample['rectangleRecomputeLimit'], 'Raster count')
        require(len(sample['components']) <= 4, 'Too many components')
        require(len({c['componentId'] for c in sample['components']}) == len(sample['components']),
                'Duplicate sampled components')
        validations = 0
        recomputations = 0
        candidate_witnesses = []
        for component in sample['components']:
            chosen = next((c for c in comparisons if c['componentId'] == component['componentId']), None)
            require(chosen is not None, 'Sampled component not in raw plan')
            require(component['chosenScore'] == chosen['chosenScore'] and
                    component['rawChosenX'] == chosen['rawX'] and
                    component['rawChosenY'] == chosen['rawY'] and
                    component['recordedChosenX'] == chosen['recordedX'] and
                    component['recordedChosenY'] == chosen['recordedY'] and
                    component['chosenComparable'] == chosen['comparable'], 'Sampled choice join')
            integer(component['sampledOrigins'], 0, 32, 'Too many sampled origins')
            require(component['sampledOrigins'] == len(component['origins']), 'Origin count mismatch')
            require(component['sampledOrigins'] <= component['recordedSamePoseSuccesses'],
                    'Sample contains unrecorded origins')
            sequences = set()
            for origin in component['origins']:
                sequence = origin['recordedOriginSequence']
                integer(sequence, 0, max(0, trace['records'] - 1), 'Invalid recorded origin')
                require(sequence not in sequences, 'Duplicate sampled origin')
                sequences.add(sequence)
                require(origin['fit'] is True and origin['poseMatches'] is True and
                        math.isfinite(origin['loggedEffectiveScore']), 'Unrecorded/invalid origin')
                require(origin['rotation'] == chosen['rawRotation'] and
                        origin['mountingSide'] == chosen['rawMountingSide'], 'Sampled pose differs')
                require(origin['fullValidation'] in ('PASS', 'REJECTED', 'SKIPPED_NO_RECT_INTERSECTION',
                        'SKIPPED_BASELINE_ORIGIN', 'SKIPPED_POSE_MISMATCH',
                        'NOT_CHECKED_VALIDATION_CAP', 'NOT_CHECKED_RECTANGLE_CAP',
                        'PASS_RECTANGLE_INCONCLUSIVE'), 'Unknown validation outcome')
                validations += origin['fullValidation'] in ('PASS', 'REJECTED', 'PASS_RECTANGLE_INCONCLUSIVE')
                if origin['fullValidation'] == 'PASS':
                    recomputations += 1
                    require(origin['strictlyReducesRawPocket'] ==
                            (origin['postMoveRectangleArea'] < rectangle['area']), 'False raw reduction')
                    if (origin['postMoveRectangleArea'] < rectangle['area'] and
                            origin['loggedEffectiveScore'] > component['chosenScore']):
                        candidate_witnesses.append((component['componentId'], sequence))
        require(validations == sample['wholePlanValidations'], 'Full validation count mismatch')
        require(recomputations == sample['rectangleRecomputations'], 'Raster recomputation count mismatch')
        w = d['witness']
        require(w['status'] in ('WITNESS', 'NO_WITNESS_IN_DECLARED_SAMPLE', 'INCONCLUSIVE'), 'Outcome')
        require(d['status'] == ('WITNESS' if w['status'] == 'WITNESS' else 'INCONCLUSIVE'),
                'Diagnostic causal status mismatch')
        bound = (d['bindingStatus'] == 'BOUND' and d['returnedPlanMatchesSelected'] and
                 not trace['capped'] and trace['chosenPlacements'] == row['parts'] and
                 len(comparisons) == row['parts'] and all(c['comparable'] for c in comparisons) and
                 rectangle['status'] == 'PASS' and not sample['capped'])
        if not bound:
            require(w['status'] == 'INCONCLUSIVE' and len(d['reasons']) > 0, 'Unbound causal claim')
        if w['status'] == 'NO_WITNESS_IN_DECLARED_SAMPLE':
            require(not candidate_witnesses, 'Sampled witness silently omitted')
        if w['status'] == 'WITNESS':
            require(bound, 'Witness lacks binding')
            require(w['beforeArea'] == rectangle['area'] and 0 <= w['afterArea'] < w['beforeArea'],
                    'Witness does not reduce raw pocket')
            require(math.isfinite(w['alternativeScore']) and math.isfinite(w['chosenScore']) and
                    w['alternativeScore'] > w['chosenScore'], 'Witness did not lose logged score')
            require(w['componentId'] in {c['componentId'] for c in sample['components']},
                    'Witness outside declared components')
            component = next(c for c in sample['components'] if c['componentId'] == w['componentId'])
            matches = [o for o in component['origins']
                       if o['recordedOriginSequence'] == w['recordedOriginSequence']]
            require(len(matches) == 1, 'Witness origin not sampled')
            origin = matches[0]
            require(origin['fullValidation'] == 'PASS' and origin['poseMatches'] is True and
                    origin['postMoveRectangleArea'] == w['afterArea'] and
                    origin['loggedEffectiveScore'] == w['alternativeScore'] and
                    component['chosenScore'] == w['chosenScore'], 'Witness join mismatch')
            for flag in ('poseMatches', 'validationPassed', 'strictlyReducesRawPocket',
                         'strictlyWorseEffectiveScore'):
                require(w[flag] is True, 'False witness flag')
        outcomes.append({'seed': seed, 'status': w['status'], 'rawArea': rectangle.get('area'),
                         'wholePlanValidations': sample['wholePlanValidations']})
    return {'status': 'PASS', 'acceptedExportsExact': True, 'cases': outcomes,
            'claim': 'Read-only bounded raw-placement measurement; no routed improvement or production acceptance'}


def main():
    log = (HERE / 'focused-native.txt').read_text(encoding='utf-8-sig')
    rows = [json.loads(line[len('PCB_POCKET_DECISION_CASE '):]) for line in log.splitlines()
            if line.startswith('PCB_POCKET_DECISION_CASE ')]
    require('PASS: PCB pocket decision diagnostic contracts ' in log, 'Missing native terminal marker')
    accepted = json.loads((HERE / 'accepted15-cases.json').read_text())['cases']
    result = assess(rows, accepted)
    result['nativeLogSha256'] = hashlib.sha256((HERE / 'focused-native.txt').read_bytes()).hexdigest()
    (HERE / 'assessment.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
