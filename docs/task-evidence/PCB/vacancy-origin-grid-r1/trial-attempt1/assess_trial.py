"""Strict finite metric reader; this does not independently prove physical routing."""
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOTS = ['-1017', '-1004', '-1022', '0', '17', '42', '1', '7', '5', '14', '2', '3', '4', '6']
BASE_SHA = '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
PREFIX = 'PCB_VACANCY_GRID_CASE '
METRICS = ('area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias')


def baseline():
    path = HERE / 'accepted15-baselines.json'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == BASE_SHA, 'Baseline seal changed'
    rows = json.loads(path.read_text())['rows']
    assert [r['seed'] for r in rows] == ROOTS
    assert all(r['status'] == 'PASS' and r['version'] == 15 and r['density'] == 14 for r in rows)
    return rows


def integer(row, key, minimum, maximum=None):
    value = row[key]
    assert type(value) is int and value >= minimum, 'Invalid integer ' + key
    assert maximum is None or value <= maximum, 'Budget exceeded ' + key
    return value


def assess(rows, bases):
    assert type(rows) is list and len(rows) == len(ROOTS), 'Missing or extra cases'
    assert [r['seed'] for r in rows] == ROOTS, 'Wrong root order, duplicate, or substituted root'
    comparisons = []
    for row, old in zip(rows, bases):
        assert row['status'] == 'PASS' and row['version'] == 16 and row['density'] == 15, 'Wrong interpretation/status'
        for key in ('parts', 'pads', 'liveBoardGraphSha256', 'sourceIntentSha256', 'layoutSeed', 'routingSeed'):
            assert type(row[key]) is type(old[key]) and row[key] == old[key], 'Identity changed: ' + key
        for key in ('sourcePlanSha256', 'geometrySha256', 'liveBoardGraphSha256', 'sourceIntentSha256'):
            assert type(row[key]) is str and re.fullmatch('[a-f0-9]{64}', row[key]), 'Invalid digest ' + key
        for key in ('layoutSeed', 'routingSeed'):
            assert re.fullmatch('-?(0|[1-9][0-9]*)', row[key]), 'Seed is not canonical signed-long text'
            assert -(2**63) <= int(row[key]) < 2**63, 'Seed outside signed long'
        width = integer(row, 'width', 1)
        height = integer(row, 'height', 1)
        area = integer(row, 'area', 1)
        assert area == width * height, 'Area differs from dimensions'
        integer(row, 'largestProtectedEmptyRectangleArea', 0, area)
        integer(row, 'copperManhattanLength', 1)
        integer(row, 'vias', 0)
        integer(row, 'acceptedPlanEvaluations', 0, 6 * 60000)
        integer(row, 'placementRejections', 0, 6)
        one = integer(row, 'oneFaceAttempts', 0, 3)
        two = integer(row, 'twoLayerAttempts', 1, 3)
        attempts = integer(row, 'routeAttempts', 1, 6)
        assert attempts == one + two, 'Inconsistent route attempt count'
        integer(row, 'routingOrders', 1)
        integer(row, 'routingExpansions', 1)
        selected = integer(row, 'selectedAttempt', 0, 5)
        ranked = row['rankedPlacementAttempts']
        assert type(ranked) is list and all(type(x) is int for x in ranked), 'Invalid candidate order'
        assert len(ranked) == len(set(ranked)) and 1 <= len(ranked) <= 6, 'Duplicate/extra candidate'
        assert all(0 <= x <= 5 for x in ranked) and selected in ranked, 'Unranked winner'
        deltas = {key: row[key] - old[key] for key in METRICS}
        comparisons.append({'seed': row['seed'], 'deltas': deltas,
            'regressions': [key for key in METRICS if deltas[key] > 0],
            'strictAreaOrVoidBenefit': deltas['area'] < 0 or deltas['largestProtectedEmptyRectangleArea'] < 0})
    regressions = [{'seed': r['seed'], 'metrics': r['regressions']} for r in comparisons if r['regressions']]
    benefit = any(r['strictAreaOrVoidBenefit'] for r in comparisons)
    return {'status': 'PASS' if not regressions and benefit else 'FAIL', 'cases': len(rows),
        'regressions': regressions, 'hasStrictAreaOrVoidBenefit': benefit, 'comparisons': comparisons,
        'scope': 'Fixed14 informed regression metric/identity/work sanity only; native validators own physical checks; no unseen or population claim',
        'limitsChanged': False}


def read_rows(path):
    return [json.loads(line[len(PREFIX):]) for line in path.read_text(encoding='utf-8-sig').splitlines()
            if line.startswith(PREFIX)]


if __name__ == '__main__':
    assert len(sys.argv) == 3, 'Expected native log and result path'
    try:
        result = assess(read_rows(Path(sys.argv[1])), baseline())
    except (AssertionError, KeyError, TypeError, ValueError) as error:
        result = {'status': 'FAIL', 'phase': 'metric-reader-validation', 'error': str(error), 'limitsChanged': False}
    Path(sys.argv[2]).write_text(json.dumps(result, indent=2) + '\n')
    print(result['status'] + ': fixed14 vacancy grid trial metric reader')
    raise SystemExit(result['status'] != 'PASS')
