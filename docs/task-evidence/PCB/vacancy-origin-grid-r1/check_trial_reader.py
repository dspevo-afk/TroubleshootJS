"""Synthetic reader canaries, executed before viewing any routed trial exports."""
import copy
import hashlib
import json
from pathlib import Path
import assess_trial as reader

HERE = Path(__file__).resolve().parent
bases = reader.baseline()
unchanged = copy.deepcopy(bases)
for row in unchanged:
    row['version'] = 16
    row['density'] = 15
improving = copy.deepcopy(unchanged)
improving[0]['largestProtectedEmptyRectangleArea'] -= 1
assert reader.assess(improving, bases)['status'] == 'PASS'
assert reader.assess(unchanged, bases)['status'] == 'FAIL'
negative = []


def reject(label, change):
    rows = copy.deepcopy(improving)
    change(rows)
    try:
        result = reader.assess(rows, bases)
        assert result['status'] == 'FAIL', label + ' unexpectedly accepted'
    except (KeyError, TypeError, ValueError):
        pass
    except AssertionError as error:
        if str(error).endswith('unexpectedly accepted'):
            raise
    negative.append(label)


reject('missing', lambda rows: rows.pop())
reject('extra', lambda rows: rows.append(copy.deepcopy(rows[-1])))
reject('duplicate', lambda rows: rows.__setitem__(1, copy.deepcopy(rows[0])))
reject('wrong-order', lambda rows: rows.reverse())
for key, value in [('seed', '99'), ('status', 'FAIL'), ('version', 15), ('density', 14),
                   ('parts', 999), ('pads', 999), ('liveBoardGraphSha256', 'a' * 64),
                   ('sourceIntentSha256', 'b' * 64), ('layoutSeed', '123'), ('routingSeed', '456'),
                   ('layoutSeed', int(improving[0]['layoutSeed'])), ('geometrySha256', 'bad'),
                   ('sourcePlanSha256', 'bad'), ('area', 1), ('width', True), ('vias', -1),
                   ('acceptedPlanEvaluations', 360001), ('oneFaceAttempts', 4), ('twoLayerAttempts', 4),
                   ('twoLayerAttempts', 0), ('routeAttempts', 7), ('selectedAttempt', 6),
                   ('rankedPlacementAttempts', [0, 0]), ('copperManhattanLength', 0)]:
    reject('invalid-' + key + '-' + str(value), lambda rows, k=key, v=value: rows[0].__setitem__(k, v))
for key in reader.METRICS:
    def regress(rows, metric=key):
        rows[0][metric] = bases[0][metric] + 1
        if metric == 'area':
            rows[0]['width'] = 1
            rows[0]['height'] = rows[0]['area']
    reject('regression-' + key, regress)
result = {'status': 'PASS', 'phase': 'Before routed trial runtime/observation',
    'readerSha256': hashlib.sha256((HERE / 'assess_trial.py').read_bytes()).hexdigest(),
    'positiveCases': 1, 'negativeCases': len(negative) + 1,
    'negativeLabels': ['unchanged-no-benefit'] + negative,
    'scope': 'Synthetic reader canaries only; no physical or trial-generation proof'}
(HERE / 'trial-reader-canaries.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS synthetic reader: 1 positive / ' + str(len(negative) + 1) + ' negative canaries')
