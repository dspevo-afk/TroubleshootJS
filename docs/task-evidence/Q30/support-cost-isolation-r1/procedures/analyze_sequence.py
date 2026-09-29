"""Read-only analysis of the frozen Q30 sequence; no timing extrapolation."""
from pathlib import Path
import csv
import hashlib
import json

REC = Path(__file__).resolve().parent
plan = json.loads((REC / 'plan.json').read_text())
rows = {}
for label, arm, mode, flag in plan['sequence']:
    file = REC / (label + '-summary.json')
    if not file.exists():
        continue
    row = json.loads(file.read_text())
    assert row['exitCode'] == 0 and row['runnerOutcome'] == 'PASS'
    assert row['cleanup']['status'] == row['inputAudit']['status'] == 'PASS'
    rows[label] = row

metrics = ('elapsedMs', 'proofElapsedMs', 'routingElapsedMs')
groups = {
    'baselineProfiler': ([('03-off-profile', '02-off'), ('04-off-profile', '05-off')], ['02-off', '05-off']),
    'structuralMaintenance': ([('06-track', '05-off'), ('09-track', '10-off')], ['02-off', '05-off', '10-off', '13-off']),
    'trackerProfiler': ([('07-track-profile', '06-track'), ('08-track-profile', '09-track')], ['06-track', '09-track']),
    'restrictedVersusFullWithoutTracker': ([('11-restricted', '10-off'), ('12-restricted', '13-off')], ['02-off', '05-off', '10-off', '13-off']),
    'rawControlCodeShape': ([('02-off', '01-control'), ('13-off', '14-control')], ['01-control', '14-control']),
}
contrasts = {}
for name, (pairs, controls) in groups.items():
    available = [rows[label]['cold']['elapsedMs'] for label in controls if label in rows]
    spread = max(available) - min(available) if len(available) > 1 else None
    threshold = max(1000, 3 * spread) if spread is not None else None
    values = []
    for candidate, reference in pairs:
        if candidate not in rows or reference not in rows:
            continue
        values.append({'candidate': candidate, 'reference': reference,
            'cold': {m: rows[candidate]['cold'][m] - rows[reference]['cold'][m] for m in metrics},
            'warmElapsedMs': rows[candidate]['warm']['elapsedMs'] - rows[reference]['warm']['elapsedMs']})
    complete = len(values) == len(pairs) and all(c in rows for c in controls)
    deltas = [v['cold']['elapsedMs'] for v in values]
    material = complete and threshold is not None and (
        all(v > threshold for v in deltas) or all(v < -threshold for v in deltas))
    contrasts[name] = {'complete': complete, 'controlLabels': controls,
        'observedColdRangeMs': spread, 'predeclaredThresholdMs': threshold,
        'bothDirectionsMaterial': material, 'pairs': values}

profiles = {}
count_fields = None
for label, row in rows.items():
    if not row['profile']:
        continue
    raw = json.loads((REC / (label + '-raw-report.json')).read_text())
    profiles[label] = {}
    for phase in ('cold', 'warm'):
        p = raw[phase]['supportCost']
        # Exact scalar work counts, separate from timer-derived sample values.
        counts = {k: v for k, v in p.items() if isinstance(v, (int, float))
                  and not isinstance(v, bool) and 'Millis' not in k
                  and 'Micros' not in k and not k.startswith('stampSample')}
        profiles[label][phase] = {'counts': counts,
            'countsSha256': hashlib.sha256(json.dumps(counts, sort_keys=True).encode()).hexdigest(),
            'timersMs': {k: v for k, v in p.items() if 'Millis' in k},
            'stampSampling': {k: v for k, v in p.items() if k.startswith('stampSample')},
            'stampsByDumpType': p['stampsByDumpType']}

consistency = []
for left, right in [('03-off-profile', '04-off-profile'), ('07-track-profile', '08-track-profile')]:
    if left in profiles and right in profiles:
        for phase in ('cold', 'warm'):
            a, b = profiles[left][phase], profiles[right][phase]
            assert a['counts'] == b['counts'], (left, right, phase, 'count mismatch')
            assert a['stampsByDumpType'] == b['stampsByDumpType'], (left, right, phase, 'type mismatch')
        consistency.append({'left': left, 'right': right, 'allWorkCountsAndTypes': 'IDENTICAL'})
if '03-off-profile' in profiles and '07-track-profile' in profiles:
    for phase in ('cold', 'warm'):
        a, b = profiles['03-off-profile'][phase]['counts'], profiles['07-track-profile'][phase]['counts']
        keys = [k for k in a if not k.startswith(('support', 'domain', 'factorsAt', 'factorsWithout'))]
        assert all(a[k] == b[k] for k in keys), (phase, 'nonstructural work differs')
    consistency.append({'left': '03-off-profile', 'right': '07-track-profile',
        'nonstructuralWorkCounts': 'IDENTICAL', 'scope': 'All scalar nonstructural counts, including numeric LU scans, copies, inserts and clears'})

out = {'completedRows': len(rows), 'plannedRows': len(plan['sequence']),
       'contrasts': contrasts, 'profiles': profiles,
       'profileWorkConsistency': consistency,
       'limits': ['Observed paired differences, not exact causal decomposition or confidence intervals.',
                  'Fine timers and stamp samples are quantized; no extrapolation or calibration subtraction.',
                  'Timer categories are nested and instrumentation materially changes code execution.',
                  'This single private seed does not qualify the frozen normal-player corpus.']}
(REC / 'analysis.json').write_text(json.dumps(out, indent=2) + '\n')
with (REC / 'timings.csv').open('w', newline='') as file:
    writer = csv.writer(file)
    writer.writerow(['label', 'arm', 'mode', 'profile', 'coldMs', 'proofMs', 'routingMs', 'warmMs', 'caseSeconds', 'outerMonotonicSeconds', 'cleanupSeconds', 'wallMinusMonotonicSeconds'])
    for label, row in rows.items():
        writer.writerow([label, row['arm'], row['mode'], row['profile'],
            *[row['cold'][m] for m in metrics], row['warm']['elapsedMs'],
            row['cases'][0]['operationSeconds'], row['outerMonotonicSeconds'],
            row['cleanup']['seconds'], row['wallMinusMonotonicSeconds']])
print(json.dumps({'completedRows': len(rows), 'contrasts': contrasts}, indent=2))
