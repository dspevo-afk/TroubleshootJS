"""Synthetic negative canaries for the independent reader, never native proof."""
import copy
import json
from assess_decisions import HERE, assess


def fixture():
    accepted = json.loads((HERE / 'accepted15-cases.json').read_text())['cases']
    rows = copy.deepcopy(accepted)
    for row in rows:
        digest = 'a' * 64
        row['selectedPlacementPlanSha256'] = digest
        row['diagnostic'] = {
            'coordinateFrame': 'RAW_PRE_PLACEMENT_COMPACTION', 'bindingStatus': 'BOUND', 'status': 'WITNESS', 'traceSeed': int(row['layoutSeed']), 'seed': int(row['seed']), 'selectedAttempt': row['selectedAttempt'], 'density': 14,
            'trace': {'records': 200, 'cap': 60000, 'capped': False, 'chosenPlacements': row['parts']},
            'rawPlanSha256': digest, 'returnedPlanSha256': digest,
            'selectedPlacementPlanSha256': digest, 'returnedPlanMatchesSelected': True,
            'rawPlanFingerprintMatchesSelected': True, 'reasons': [],
            'chosenComparisons': [{'componentId': 'C' + str(i), 'rawX': i, 'rawY': 0,
                'recordedX': i, 'recordedY': 0, 'comparable': True, 'chosenScore': 1.0, 'choiceFound': True, 'coordinatesMatch': True, 'poseMatches': True, 'rawRotation': 'R0', 'recordedRotation': 'R0', 'rawMountingSide': 'TOP', 'recordedMountingSide': 'TOP'}
                for i in range(row['parts'])],
            'rawAccessEnvelopeRectangle': {'status': 'PASS', 'grid': 10, 'cells': 100,
                'area': 10000, 'x': 0, 'y': 0, 'width': 100, 'height': 100, 'rasterCells': 100, 'emptyRectangleCells': 100, 'domainCellCount': 100, 'domainPolicy': 'FIXED_RAW_ENVELOPE_BOUNDS_CLIPPED', 'domainX': 0, 'domainY': 0, 'domainWidth': 100, 'domainHeight': 100,
                'occupancy': 'UNION_OF_FULL_ACCESS_ENVELOPES'},
            'sample': {'componentLimit': 4, 'originsPerComponentLimit': 32,
                'wholePlanValidationLimit': 128, 'wholePlanValidations': 1,
                'rectangleRecomputeLimit': 16, 'rectangleRecomputations': 1, 'capped': False,
                'components': [{'componentId': 'C0', 'recordedSamePoseSuccesses': 1,
                    'chosenScore': 1.0, 'rawChosenX': 0, 'rawChosenY': 0, 'recordedChosenX': 0, 'recordedChosenY': 0, 'chosenComparable': True, 'sampledOrigins': 1, 'origins': [{'recordedOriginSequence': 0,
                        'x': 0, 'y': 10, 'rotation': 'R0', 'mountingSide': 'TOP', 'fit': True,
                        'loggedEffectiveScore': 2.0, 'poseMatches': True, 'fullValidation': 'PASS',
                        'postMoveRectangleArea': 9000, 'strictlyReducesRawPocket': True}]}]},
            'witness': {'status': 'WITNESS', 'componentId': 'C0', 'recordedOriginSequence': 0,
                'poseMatches': True, 'validationPassed': True, 'strictlyReducesRawPocket': True,
                'strictlyWorseEffectiveScore': True, 'chosenScore': 1.0, 'alternativeScore': 2.0,
                'beforeArea': 10000, 'afterArea': 9000, 'reasons': []}}
    return rows, accepted


def main():
    rows, accepted = fixture()
    assess(rows, accepted)
    cases = []
    def reject(name, mutate):
        altered = copy.deepcopy(rows)
        mutate(altered)
        try:
            assess(altered, accepted)
        except (ValueError, KeyError, TypeError):
            cases.append(name)
        else:
            raise AssertionError('Reader admitted ' + name)
    def field(path, value):
        def change(data):
            obj = data[0]
            for key in path[:-1]:
                obj = obj[key]
            obj[path[-1]] = value
        return change
    reject('missing-root', lambda data: data.pop())
    reject('duplicate-root', lambda data: data.__setitem__(1, copy.deepcopy(data[0])))
    for key in ('area', 'selectedAttempt', 'routingOrders', 'acceptedPlanEvaluations', 'geometrySha256'):
        reject('accepted-' + key, field([key], 'changed'))
    reject('mixed-frame', field(['diagnostic', 'coordinateFrame'], 'FINAL_ROUTED'))
    reject('trace-cap', field(['diagnostic', 'trace', 'capped'], True))
    reject('trace-overrun', field(['diagnostic', 'trace', 'records'], 60001))
    reject('binding-false', field(['diagnostic', 'returnedPlanMatchesSelected'], False))
    reject('raw-binding-lie', field(['diagnostic', 'rawPlanFingerprintMatchesSelected'], False))
    reject('raster-overrun', field(['diagnostic', 'rawAccessEnvelopeRectangle', 'cells'], 1000001))
    reject('rectangle-area', field(['diagnostic', 'rawAccessEnvelopeRectangle', 'width'], 101))
    reject('validation-overrun', field(['diagnostic', 'sample', 'wholePlanValidations'], 129))
    reject('validation-count', field(['diagnostic', 'sample', 'wholePlanValidations'], 0))
    reject('witness-not-reduced', field(['diagnostic', 'witness', 'afterArea'], 10000))
    reject('witness-better-score', field(['diagnostic', 'witness', 'alternativeScore'], 0.5))
    reject('witness-unjoined', field(['diagnostic', 'witness', 'recordedOriginSequence'], 3))
    reject('witness-false-validation', field(['diagnostic', 'witness', 'validationPassed'], False))
    reject('unrecorded-origin', field(['diagnostic', 'sample', 'components', 0, 'origins', 0, 'fit'], False))
    reject('wrong-pose', field(['diagnostic', 'sample', 'components', 0, 'origins', 0, 'poseMatches'], False))
    reject('rejected-witness', field(['diagnostic', 'sample', 'components', 0, 'origins', 0,
                                    'fullValidation'], 'REJECTED'))
    reject('silently-omitted-witness', field(['diagnostic', 'witness', 'status'], 'NO_WITNESS_IN_DECLARED_SAMPLE'))
    reject('sample-cap-causal-claim', field(['diagnostic', 'sample', 'capped'], True))
    reject('moving-domain', field(['diagnostic', 'rawAccessEnvelopeRectangle', 'domainPolicy'], 'CURRENT_BOUNDS'))
    reject('sampled-score-substitution', field(['diagnostic', 'sample', 'components', 0, 'chosenScore'], 0.1))
    reject('sampled-pose-substitution', field(['diagnostic', 'sample', 'components', 0, 'origins', 0, 'rotation'], 'R90'))
    capped = copy.deepcopy(rows)
    for row in capped:
        row['diagnostic']['trace']['capped'] = True
        row['diagnostic']['reasons'] = ['TRACE_CAP']
        row['diagnostic']['status'] = 'INCONCLUSIVE'
        row['diagnostic']['witness'] = {'status': 'INCONCLUSIVE', 'reasons': ['TRACE_CAP']}
    assess(capped, accepted)
    no_witness = copy.deepcopy(rows)
    for row in no_witness:
        origin = row['diagnostic']['sample']['components'][0]['origins'][0]
        origin['postMoveRectangleArea'] = 10000
        origin['strictlyReducesRawPocket'] = False
        row['diagnostic']['status'] = 'INCONCLUSIVE'
        row['diagnostic']['witness'] = {'status': 'NO_WITNESS_IN_DECLARED_SAMPLE', 'reasons': []}
    assess(no_witness, accepted)
    result = {'status': 'PASS', 'negativeCases': cases, 'negativeCaseCount': len(cases),
              'positiveCases': ['bound-witness', 'honest-capped-inconclusive', 'finite-no-witness'],
              'scope': 'Synthetic reader guards only; no native or layout proof'}
    (HERE / 'assessor-canaries.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
