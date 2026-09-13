"""Strict Q15 acceptance reader: frozen cohort, real-stage coverage and budgets."""
import copy
import json
import math
import pathlib
import sys


SEEDS = ['0', '1', '2', '3', '17', '42', '101', '-1', '9007199254740993',
         '-9223372036854775808', '9223372036854775807']
DESIGNS = ['NMOS_RESISTIVE_BIAS', 'BJT_RESISTIVE_BIAS', 'NMOS_RESISTIVE_BIAS',
           'NMOS_RC_FILTER', 'NMOS_RC_FILTER', 'NMOS_RC_FILTER', 'BJT_RC_FILTER',
           'NMOS_RESISTIVE_BIAS', 'NMOS_RC_FILTER', 'BJT_RESISTIVE_BIAS',
           'BJT_RESISTIVE_BIAS']
FAULTS = {'BASE_RESISTOR_OPEN', 'RELAY_COIL_OPEN', 'RELAY_CONTACT_OPEN'}
COPPER_PAIRS = [('J1.1', 'F1.1'), ('J1.2', 'RBLEED.2'), ('J2.1', 'RIN.1')]


def require(ok, message):
    if not ok:
        raise ValueError(message)


def integer(value, lower=0, upper=2**53-1):
    require(type(value) is int and lower <= value <= upper, 'invalid integer or budget')


def validate(report):
    require(report.get('protocol') == 'TSJ-Q15-1' and report.get('status') == 'PASS',
            'missing complete Q15 proof')
    require(report.get('ownerRestored') is True and report.get('failure') is None,
            'failed or unproved cleanup')
    integer(report.get('assertions'), 100)
    integer(report.get('supportAssertions'), 76)
    integer(report.get('cancellationMs'), 0, 500)
    integer(report.get('elapsedMs'), 1)
    integer(report.get('cleanupMs'))
    cases = report.get('cases')
    require(type(cases) is list and len(cases) == 11, 'incomplete frozen cohort')
    require([row.get('seed') for row in cases] == SEEDS, 'wrong, rounded or duplicated seeds')
    require({row.get('fault') for row in cases} == FAULTS, 'missing fault population')
    for row, design in zip(cases, DESIGNS):
        require(row.get('design') == 'RB15_' + design, 'wrong structural design')
        for field, count in [('packages', 16), ('hypotheses', 3), ('stages', 6)]:
            integer(row.get(field), count, count)
        integer(row.get('admissionMs'), 1, 90000)
        integer(row.get('maxUnitMs'), 0, 5000)
        integer(row.get('workUnits'), 6, 640)
        integer(row.get('repairMs'), 1)
        integer(row.get('supportMs'))
        continuity = row.get('continuity')
        expected = [pair for a, b in COPPER_PAIRS for pair in [(a, b), (b, a)]]
        require(type(continuity) is list and len(continuity) == len(expected),
                'missing two-direction copper continuity proof')
        for sample, pair in zip(continuity, expected):
            require((sample.get('red'), sample.get('black')) == pair,
                    'wrong or duplicated continuity endpoints')
            ohms = sample.get('ohms')
            require(type(ohms) in (int, float) and math.isfinite(ohms) and 0 <= ohms < .001,
                    'continuous copper did not measure zero')
        integer(row.get('routeAttempts'), 1, 400)
        integer(row.get('routeExpansions'), 1)
        integer(row.get('routeMs'), 0, row['admissionMs'])
        stages = row.get('stageMs')
        require(type(stages) is list and len(stages) == 6, 'missing stage timings')
        for value in stages:
            integer(value, 0, row['admissionMs'])
        require(sum(stages) <= row['admissionMs'], 'stage time exceeds admission time')
    return report


def rejects(report):
    try:
        validate(report)
        return False
    except (ValueError, KeyError, TypeError):
        return True


def main(root):
    report = validate(json.loads((root / 'compiled-control-board.json').read_text(encoding='utf-8-sig')))
    forced = json.loads((root / 'forced-negative.json').read_text(encoding='utf-8-sig'))
    require(forced.get('protocol') == 'TSJ-Q15-1' and forced.get('status') == 'FAIL'
            and forced.get('ownerRestored') is True and forced.get('cases') == []
            and 'q15-explicit-failure-canary' in forced.get('failure', ''),
            'forced negative did not fail closed and restore its owner')
    integer(forced.get('elapsedMs'))
    integer(forced.get('cleanupMs'))
    malformed = [{}, {'status': 'PASS'}]
    for field, value in [('cases', report['cases'][:-1]), ('ownerRestored', False),
                         ('supportAssertions', '100'), ('cancellationMs', 501)]:
        candidate = copy.deepcopy(report)
        candidate[field] = value
        malformed.append(candidate)
    for field, value in [('seed', 0), ('seed', '1'), ('design', 'RB15_BJT_RC_FILTER'),
                         ('admissionMs', 90001), ('maxUnitMs', 5001), ('workUnits', 641),
                         ('routeAttempts', 401), ('stageMs', [0]), ('hypotheses', 2),
                         ('packages', True)]:
        candidate = copy.deepcopy(report)
        candidate['cases'][0][field] = value
        malformed.append(candidate)
    candidate = copy.deepcopy(report)
    candidate['cases'][0].pop('continuity')
    malformed.append(candidate)
    candidate = copy.deepcopy(report)
    candidate['cases'][0]['continuity'].pop()
    malformed.append(candidate)
    for field, value in [('black', 'J1.1'), ('ohms', 3), ('ohms', float('nan')),
                         ('ohms', True)]:
        candidate = copy.deepcopy(report)
        candidate['cases'][0]['continuity'][0][field] = value
        malformed.append(candidate)
    require(all(rejects(candidate) for candidate in malformed), 'reader accepted malformed proof')
    print(f'PASS: Q15 eleven cases/four designs/three faults; forced cleanup; {len(malformed)} reader negatives')


if __name__ == '__main__':
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'docs/task-evidence/Q15'))
