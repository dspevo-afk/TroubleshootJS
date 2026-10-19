"""Bounded offline comparison, not a generator or routing experiment."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent

def remaining(pocket, hole):
    x, y, width, height = (pocket[k] for k in ('x', 'y', 'width', 'height'))
    left = max(x, hole['x'])
    top = max(y, hole['y'])
    right = min(x + width, hole['x'] + hole['width'])
    bottom = min(y + height, hole['y'] + hole['height'])
    if left >= right or top >= bottom:
        return width * height
    return max((left - x) * height, (x + width - right) * height,
               width * (top - y), width * (y + height - bottom))

def exhaustive(pocket, hole):
    best = 0
    for left in range(pocket['x'], pocket['x'] + pocket['width']):
        for right in range(left + 1, pocket['x'] + pocket['width'] + 1):
            for top in range(pocket['y'], pocket['y'] + pocket['height']):
                for bottom in range(top + 1, pocket['y'] + pocket['height'] + 1):
                    if right <= hole['x'] or left >= hole['x'] + hole['width'] or bottom <= hole['y'] or top >= hole['y'] + hole['height']:
                        best = max(best, (right - left) * (bottom - top))
    return best

def main():
    proposal = json.loads((HERE / 'proposal.json').read_text())
    source = HERE.parent / proposal['sourceLog']
    data = source.read_bytes()
    assert hashlib.sha256(data).hexdigest() == proposal['sourceLogSha256']
    prefix = 'PCB_PLACEMENT_SCORE_TERMS_CASE '
    rows = [json.loads(line[len(prefix):]) for line in data.decode('utf-8-sig').splitlines() if line.startswith(prefix)]
    row, = rows
    assert row['seed'] == '42' and row['attempt'] == 1 and row['componentId'] == 'QA'
    pocket = row['context']['vacancyPocket']
    fixtures = []
    small = {'x': -2, 'y': -1, 'width': 6, 'height': 5}
    for hole, expected in (({'x': 0, 'y': 0, 'width': 2, 'height': 2}, 12),
                           ({'x': 4, 'y': 0, 'width': 2, 'height': 2}, 30),
                           ({'x': -3, 'y': -2, 'width': 8, 'height': 7}, 0),
                           ({'x': -3, 'y': 0, 'width': 2, 'height': 2}, 25)):
        analytic = remaining(small, hole)
        brute = exhaustive(small, hole)
        assert analytic == brute == expected
        fixtures.append({'pocket': small, 'hole': hole, 'literalExpected': expected, 'analytic': analytic, 'exhaustive': brute})
    origins = [o for o in row['origins'] if o['componentId'] == 'QA']
    assert 0 < len(origins) <= proposal['existingOriginsCap']
    choices = []
    area = pocket['width'] * pocket['height']
    for origin in origins:
        if not origin['fits']:
            continue
        assert origin['rotation'] == 'DEG_0' and origin['mountingSide'] == 'TOP'
        residual = remaining(pocket, origin['accessEnvelope'])
        reduction = area - residual
        choices.append({'sequence': origin['sequence'], 'x': origin['x'], 'y': origin['y'],
                        'existingScore': origin['effectiveScore'], 'largestResidualRectangleArea': residual,
                        'pocketAreaReduction': reduction,
                        'counterfactualScore': origin['effectiveScore'] - reduction / 40.0})
    assert choices
    winner = min(choices, key=lambda o: o['counterfactualScore'])
    observed = next(o for o in choices if o['sequence'] == row['chosen']['sequence'])
    result = {'status': 'PASS', 'scope': proposal['scope'], 'nativeOrRoutingExecuted': False,
              'sourceLogSha256': proposal['sourceLogSha256'], 'advisoryPocket': pocket,
              'observedOrigins': len(origins), 'successfulOrigins': len(choices),
              'geometryFixtures': fixtures, 'observedChoice': observed, 'counterfactualChoice': winner,
              'changesExistingChoice': winner['sequence'] != observed['sequence'], 'choices': choices,
              'limits': proposal['limits']}
    (HERE / 'analysis.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: result[k] for k in ('status', 'observedOrigins', 'successfulOrigins', 'observedChoice', 'counterfactualChoice', 'changesExistingChoice')}))

if __name__ == '__main__':
    main()
