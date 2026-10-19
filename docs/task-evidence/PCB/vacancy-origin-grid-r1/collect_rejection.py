"""Preserve a partial diagnostic summary without weakening the sealed14-case gate."""
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
lines = (HERE / 'trial-native.txt').read_text(encoding='utf-8-sig').splitlines()
rows = [json.loads(s[len('PCB_VACANCY_GRID_CASE '):]) for s in lines if s.startswith('PCB_VACANCY_GRID_CASE ')]
inputs = [json.loads(s[len('PCB_VACANCY_GRID_INPUT '):]) for s in lines if s.startswith('PCB_VACANCY_GRID_INPUT ')]
whitespace = [json.loads(s[len('PCB_WHITESPACE_CASE '):]) for s in lines if s.startswith('PCB_WHITESPACE_CASE ')]
bases = {r['seed']: r for r in json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']}
assert [r['seed'] for r in rows] == ['-1017', '-1004', '-1022', '0', '17']
assert [r['seed'] for r in inputs] == ['-1017', '-1004', '-1022', '0', '17', '42']
failing = [r for r in whitespace if r['seed'] == '42']
assert len(failing) == 1 and failing[0]['largestProtectedEmptyRectangle']['area'] == 772200
metrics = ('area', 'largestProtectedEmptyRectangleArea', 'copperManhattanLength', 'vias')
partial = []
for row in rows:
    old = bases[row['seed']]
    assert all(row[k] == old[k] for k in metrics) and row['geometrySha256'] == old['geometrySha256']
    partial.append({'seed': row['seed'], 'geometryExact15': True,
        'metrics': {k: row[k] for k in metrics}, 'deltas': {k: row[k] - old[k] for k in metrics}})
assert any(s.startswith('PCB_VACANCY_GRID_FAILURE seed=42 ') for s in lines)
assert json.loads((HERE / 'trial-assessment.json').read_text())['status'] == 'FAIL'
result = {'status': 'REJECTED', 'nativeGate': 'FAIL', 'sealedMetricReader': 'FAIL: missing cases; no partial promotion',
    'selectedSuites': 11, 'startedSuites': 7, 'passedSuites': 6, 'failedSuites': 1, 'notRunSuites': 4,
    'elapsedSeconds': 79.2387563, 'nativeExitCode': 1,
    'rawOtherCandidates': 'PASS:15 unchanged bindings for0/2/3/4/5 at roots-1022/42/4',
    'completePhysicalRows': partial,
    'failingRoot': {'seed': '42', 'phase': 'Existing reportWhitespace bound after initial geometry/routing/quality validation, before remaining observation checks',
        'largestProtectedEmptyRectangleArea': 772200, 'accepted15': 178200,
        'delta': 594000, 'existingStrictUpperBound': 327600,
        'remainingPlacementWorkPurityAndFullMetricExport': 'NOT COMPLETED',
        'areaCopperViaQualityMetrics': 'NOT EXPORTED; not inferred'},
    'notRoutedRoots': ['1', '7', '5', '14', '2', '3', '4', '6'],
    'cleanup': 'PASS: maintained terminal scratch cleanup and both recorded driver creations exited; duration not separately measured',
    'promotionGwtPlayer': 'NOT RUN after native rejection', 'main1475Exact': True,
    'scope': 'Partial informed regression diagnostic only; no all14 or population acceptance', 'oracleAndBudgetsChanged': False}
(HERE / 'rejection-summary.json').write_text(json.dumps(result, indent=2) + '\n')
print('REJECTED: five exact15 completed rows; root42 void772200 fails existing bound327600; eight roots not routed')
