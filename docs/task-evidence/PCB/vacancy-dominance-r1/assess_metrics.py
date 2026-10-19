"""Strict finite-cohort acceptance; never a substitute for physical or player gates."""
import argparse
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOTS = {'-1017','-1004','-1022','0','17','42','1','7','5','14'}


def assess(baseline, rows):
    expected = {case['seed']:case for case in baseline['cases']}
    if len(baseline['cases']) != 10 or set(expected) != ROOTS:
        raise ValueError('Baseline cohort is not the frozen ten roots')
    measured = {}
    for row in rows:
        seed = row['seed']
        if seed in measured:
            raise ValueError('Duplicate measured seed '+seed)
        measured[seed] = row
    if set(measured) != ROOTS:
        raise ValueError('Measured cohort must equal frozen ten roots')
    comparisons = []
    for seed, old in expected.items():
        new = measured[seed]
        for key in ('version','density','parts','area','copperManhattanLength','vias','largestProtectedEmptyRectangleArea'):
            if type(new[key]) is not int or new[key] < 0:
                raise ValueError('Invalid integer metric '+key+' for '+seed)
        if new['version'] != 14 or new['density'] != 13 or new['parts'] != old['parts']:
            raise ValueError('Wrong interpretation/density/part census for '+seed)
        comparisons.append(dict(seed=seed, areaDelta=new['area']-old['area'],
            protectedVoidDelta=new['largestProtectedEmptyRectangleArea']-old['largestProtectedEmptyRectangle']['area'],
            copperDelta=new['copperManhattanLength']-old['copperLength'], viaDelta=new['vias']-old['vias']))
    no_regression = all(all(case[key] <= 0 for key in ('areaDelta','protectedVoidDelta','copperDelta','viaDelta')) for case in comparisons)
    improvement = any(case['areaDelta'] < 0 or case['protectedVoidDelta'] < 0 for case in comparisons)
    return dict(status='PASS' if no_regression and improvement else 'FAIL',
                noCaseRegresses=no_regression, atLeastOneStrictImprovement=improvement,
                cases=comparisons, scope='Metric acceptance only; full identity/native/GWT/player gates remain separate')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    raw = (HERE/'published13-baselines.json').read_bytes()
    if hashlib.sha256(raw).hexdigest() != '0495c3da32783a52372a6afc2875dcc6cf8d6ae1c8c2e745b4daaf5b74dd65ac':
        raise ValueError('Frozen accepted13 baseline bytes changed')
    rows, whitespace, saved_voids, saved_geometry = [], {}, {}, {}
    for line in args.log.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('PCB_CANDIDATE_ZERO_CASE '):
            rows.append(json.loads(line.split(' ',1)[1]))
        elif line.startswith('PCB_WHITESPACE_CASE '):
            item = json.loads(line.split(' ',1)[1])
            if item['version'] == 13:
                if item['seed'] in saved_voids:
                    raise ValueError('Duplicate saved13 whitespace receipt')
                saved_voids[item['seed']] = item
                continue
            if item['version'] != 14 or item['seed'] in whitespace:
                raise ValueError('Wrong or duplicate whitespace receipt')
            whitespace[item['seed']] = item
        elif line.startswith('PCB_OCCUPANCY_DIAGNOSTIC '):
            item = json.loads(line.split(' ',1)[1])
            if item['seed'] in saved_geometry:
                raise ValueError('Duplicate saved13 geometry receipt')
            saved_geometry[item['seed']] = item
    if set(whitespace) != ROOTS:
        raise ValueError('Missing actual whitespace receipt')
    for row in rows:
        if row['largestProtectedEmptyRectangleArea'] != whitespace[row['seed']]['largestProtectedEmptyRectangle']['area']:
            raise ValueError('Metric disagrees with actual whitespace receipt')
    baseline = json.loads(raw)
    if set(saved_voids) != {'42','-1022'} or set(saved_geometry) != {'42','-1022'}:
        raise ValueError('Missing fresh unchanged13 diagnostic cases')
    for old in baseline['cases']:
        seed = old['seed']
        if seed in saved_geometry:
            case = saved_geometry[seed]
            if (case['placementVersion'] != 13 or case['placementDensityVersion'] != 12 or
                case['geometryFingerprintBefore'] != old['geometrySha256'] or
                case['geometryFingerprintAfter'] != old['geometrySha256'] or
                saved_voids[seed]['largestProtectedEmptyRectangle']['area'] != old['largestProtectedEmptyRectangle']['area']):
                raise ValueError('Accepted13 geometry changed: '+seed)
    result = assess(baseline, rows)
    result['freshUnchanged13Diagnostics'] = 'PASS: roots42/-1022 exact accepted13 geometry and voids'
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))
    raise SystemExit(0 if result['status']=='PASS' else 1)
